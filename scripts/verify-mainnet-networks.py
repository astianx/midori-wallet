#!/usr/bin/env python3
"""Read-only checks against the networks used by the mainnet APK. No wallet needed."""
import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen


def fetch(url, payload=None):
    request = Request(url, data=json.dumps(payload).encode() if payload else None,
                      headers={"Content-Type": "application/json", "User-Agent": "Midori-network-check/0.1"})
    with urlopen(request, timeout=20) as response:
        return json.load(response)


def rpc(url, method, params):
    response = fetch(url, {"jsonrpc": "2.0", "id": 1, "method": method, "params": params})
    if "error" in response:
        raise ValueError(f"{method}: {response['error']}")
    return response["result"]


def main():
    source = (Path(__file__).resolve().parents[1] /
              "app/src/main/java/org/astianx/midori/one/WalletNetwork.kt").read_text()
    chain_id = int(re.search(r'const val CHAIN_ID = (\d+)', source)[1])
    contract = re.search(r'const val USDC_CONTRACT = "([^"]+)"', source)[1]
    usdt_contract = re.search(r'const val USDT_CONTRACT = "([^"]+)"', source)[1]
    configured_rpc = re.search(r'const val RPC_URL = "([^"]+)"', source)[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-rpc", default=configured_rpc)
    parser.add_argument("--bitcoin-api", default="https://blockbook.btc.zelcore.io/api")
    args = parser.parse_args()
    actual_chain = int(rpc(args.base_rpc, "eth_chainId", []), 16)
    if actual_chain != chain_id:
        raise ValueError(f"Wrong Base chain: expected {chain_id}, got {actual_chain}")
    tokens = {}
    for symbol, token in [("USDC", contract), ("USDT", usdt_contract)]:
        code = rpc(args.base_rpc, "eth_getCode", [token, "latest"])
        if code in ("0x", "0x0", ""):
            raise ValueError(f"{symbol} contract has no deployed code")
        def call(data):
            return rpc(args.base_rpc, "eth_call", [{"to": token, "data": data}, "latest"])
        decimals = int(call("0x313ce567"), 16)
        if decimals != 6:
            raise ValueError(f"Wrong {symbol} decimals: {decimals}")
        raw_symbol = bytes.fromhex(call("0x95d89b41")[2:])
        offset = int.from_bytes(raw_symbol[:32], "big")
        length = int.from_bytes(raw_symbol[offset:offset + 32], "big")
        actual_symbol = raw_symbol[offset + 32:offset + 32 + length].decode()
        if actual_symbol != symbol:
            raise ValueError(f"Wrong token symbol: expected {symbol}, got {actual_symbol}")
        balance = int(call("0x70a08231" + "0" * 63 + "1"), 16)
        tokens[symbol] = {"contract": token, "decimals": decimals, "symbol": actual_symbol,
                          "balance_of_public_probe": str(balance)}
        if symbol == "USDT":
            l1_token = "0x" + call("0xc01e1bd6")[-40:]
            bridge = "0x" + call("0xae1f6aaf")[-40:]
            if l1_token.lower() != "0xdac17f958d2ee523a2206206994597c13d831ec7":
                raise ValueError(f"Unexpected USDT origin: {l1_token}")
            if bridge.lower() != "0x4200000000000000000000000000000000000010":
                raise ValueError(f"Unexpected USDT bridge: {bridge}")
            tokens[symbol].update(l1_token=l1_token, l2_bridge=bridge)
    oracle = "0x420000000000000000000000000000000000000F"
    l1_fee = int(rpc(args.base_rpc, "eth_call", [{"to": oracle,
        "data": "0xf1c7a58b" + hex(324)[2:].zfill(64)}, "latest"]), 16)
    operator_fee = int(rpc(args.base_rpc, "eth_call", [{"to": oracle,
        "data": "0x275aedd2" + hex(65000)[2:].zfill(64)}, "latest"]), 16)
    status = fetch(args.bitcoin_api)
    btc_chain = status["backend"]["chain"]
    if btc_chain != "main":
        raise ValueError(f"Wrong Bitcoin chain: expected main, got {btc_chain}")
    genesis = fetch(args.bitcoin_api.rstrip("/") + "/v2/block-index/0")["blockHash"]
    expected_genesis = re.search(r'const val GENESIS_HASH = "([^"]+)"', source)[1]
    if genesis != expected_genesis:
        raise ValueError(f"Wrong Bitcoin genesis: {genesis}")
    print(json.dumps({
        "checked_at_utc": datetime.now(timezone.utc).isoformat(),
        "base_rpc": args.base_rpc, "chain_id": actual_chain,
        "tokens": tokens,
        "l1_fee_upper_bound_probe_wei": str(l1_fee), "operator_fee_probe_wei": str(operator_fee),
        "bitcoin_api": args.bitcoin_api, "bitcoin_chain": btc_chain, "bitcoin_genesis": genesis,
        "scope": "Read-only network checks; not funded transfer or device acceptance"
    }, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"Network verification failed: {error}", file=sys.stderr)
        sys.exit(1)
