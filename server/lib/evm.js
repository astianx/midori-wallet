import { getAddress } from 'ethers';
import { evmNetworks, stablecoinNetwork, networkAsset } from '../../config/stablecoin-networks.js';
import { evmToken, evmRecipient, inspectEvmTransfer, EVM_MAX_FEE, evmSame } from '../../packages/wallet-core/src/evm.js';
import { ApiError, exactKeys, requireValue } from './errors.js';
const quantity = value => `0x${BigInt(value).toString(16)}`;
const hashPattern = /^0x[0-9a-f]{64}$/i;
export function evmGateway({ fetchImpl = fetch, rpcUrls = {} } = {}) {
  function network(id) {
    const found = stablecoinNetwork(id);
    requireValue(evmNetworks.includes(found), 'Unsupported network');
    return found;
  }
  async function rpc(chain, method, params = []) {
    try {
      const url = rpcUrls[chain.id] || chain.rpcUrl;
      if (new URL(url).protocol !== 'https:') throw new Error();
      const response = await fetchImpl(url, { redirect: 'error', method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }), signal: AbortSignal.timeout(15000) });
      const body = await response.json();
      if (!response.ok || body.error || !Object.hasOwn(body, 'result')) throw new Error();
      return body.result;
    } catch { throw new ApiError(502, 'CHAIN_UNAVAILABLE', 'Network is temporarily unavailable'); }
  }
  async function connect(id) {
    const chain = network(id);
    if (BigInt(await rpc(chain, 'eth_chainId')) !== BigInt(chain.chainId)) throw new ApiError(502, 'PAYMENT_NETWORK_MISMATCH', 'Unexpected RPC network');
    return chain;
  }
  async function balances(chain, owner, asset) {
    const block = await rpc(chain, 'eth_blockNumber');
    const [value, native] = await Promise.all([
      rpc(chain, 'eth_call', [{ to: asset.contract, data: evmToken.encodeFunctionData('balanceOf', [owner]) }, block]),
      rpc(chain, 'eth_getBalance', [owner, block]),
    ]);
    return { balanceAtomic: BigInt(value).toString(), nativeBalanceAtomic: BigInt(native).toString(), blockNumber: BigInt(block).toString() };
  }
  return {
    async balance(id, owner, contract) {
      const chain = await connect(id), asset = networkAsset(chain, contract);
      requireValue(asset, 'Unsupported token');
      return { network: id, chainId: chain.chainId, contract: asset.contract, address: getAddress(owner), readAt: new Date().toISOString(), ...await balances(chain, owner, asset) };
    },
    async quote(id, owner, body) {
      exactKeys(body, ['contract', 'to', 'amountAtomic']);
      const chain = await connect(id), asset = networkAsset(chain, body.contract);
      requireValue(asset && typeof body.amountAtomic === 'string' && /^[1-9][0-9]{0,77}$/.test(body.amountAtomic) && BigInt(body.amountAtomic) < 2n ** 256n);
      let to;
      try { to = evmRecipient(body.to, owner, asset.contract); } catch { throw new ApiError(400, 'INVALID_RECIPIENT', 'Invalid recipient'); }
      const [balance, nonce, price] = await Promise.all([balances(chain, owner, asset), rpc(chain, 'eth_getTransactionCount', [owner, 'pending']), rpc(chain, 'eth_gasPrice')]);
      if (BigInt(balance.balanceAtomic) < BigInt(body.amountAtomic)) throw new ApiError(400, 'INSUFFICIENT_FUNDS', 'Insufficient token balance');
      if (BigInt(balance.nativeBalanceAtomic) === 0n) throw new ApiError(400, 'INSUFFICIENT_GAS_FUNDS', 'Insufficient native coin balance');
      const data = evmToken.encodeFunctionData('transfer', [to, BigInt(body.amountAtomic)]);
      const estimated = BigInt(await rpc(chain, 'eth_estimateGas', [{ from: owner, to: asset.contract, data, value: '0x0' }]));
      const gasLimit = (estimated * 120n + 99n) / 100n, gasPrice = (BigInt(price) * 125n + 99n) / 100n, fee = gasLimit * gasPrice;
      requireValue(gasLimit > 0n && gasLimit <= 1000000n && gasPrice > 0n && gasPrice <= 10n ** 14n && fee <= EVM_MAX_FEE, 'Network cost exceeds limits');
      if (BigInt(balance.nativeBalanceAtomic) < fee) throw new ApiError(400, 'INSUFFICIENT_GAS_FUNDS', 'Insufficient native coin balance');
      const nonceValue = Number(BigInt(nonce));
      requireValue(Number.isSafeInteger(nonceValue) && nonceValue >= 0 && nonceValue <= 0xffffffff);
      return { network: id, chainId: chain.chainId, from: getAddress(owner), to, contract: asset.contract, symbol: asset.symbol, decimals: asset.decimals, amountAtomic: body.amountAtomic, nonce: nonceValue, gasLimit: String(gasLimit), gasPrice: String(gasPrice), maxFeeAtomic: String(fee), nativeSymbol: chain.nativeSymbol, expiresAt: new Date(Date.now() + 120000).toISOString() };
    },
    async broadcast(id, owner, body) {
      exactKeys(body, ['raw']);
      const chain = await connect(id);
      let parsed;
      try { parsed = inspectEvmTransfer(body.raw, chain, owner); } catch { throw new ApiError(400, 'INVALID_REQUEST', 'Invalid signed transfer'); }
      const known = await rpc(chain, 'eth_getTransactionByHash', [parsed.hash]);
      if (known) return { hash: parsed.hash, status: 'submitted' };
      const nonce = BigInt(await rpc(chain, 'eth_getTransactionCount', [owner, 'pending']));
      if (nonce !== BigInt(parsed.tx.nonce)) throw new ApiError(409, 'PAYMENT_TRACKING_REQUIRED', 'Account nonce changed; check the payment');
      const result = await rpc(chain, 'eth_sendRawTransaction', [body.raw]);
      if (!evmSame(result, parsed.hash)) throw new ApiError(502, 'NETWORK_UNCONFIRMED', 'Unconfirmed broadcast');
      return { hash: parsed.hash, status: 'submitted' };
    },
    async status(id, owner, hash) {
      requireValue(hashPattern.test(hash));
      const chain = await connect(id), tx = await rpc(chain, 'eth_getTransactionByHash', [hash]);
      if (!tx) return { hash, status: 'ambiguous' };
      requireValue(evmSame(tx.from, owner) || evmSame(tx.to, owner));
      const receipt = await rpc(chain, 'eth_getTransactionReceipt', [hash]);
      if (!receipt) return { hash, status: 'pending' };
      const latest = BigInt(await rpc(chain, 'eth_blockNumber'));
      const canonical = await rpc(chain, 'eth_getBlockByNumber', [receipt.blockNumber, false]);
      if (!canonical || !evmSame(canonical.hash, receipt.blockHash)) return { hash, status: 'pending' };
      const confirmations = latest - BigInt(receipt.blockNumber) + 1n;
      const asset = networkAsset(chain,tx.to);
      let transferred = false;
      try {
        const payment = evmToken.parseTransaction({ data: tx.input });
        transferred = !!asset && receipt.logs.some(log => {
          if (!evmSame(log.address,asset.contract) || log.removed) return false;
          try { const event = evmToken.parseLog(log); return evmSame(event.args.from,owner) && evmSame(event.args.to,payment.args[0]) && event.args.value === payment.args[1]; } catch { return false; }
        });
      } catch { /* Missing transfer evidence cannot confirm delivery. */ }
      const failed = BigInt(receipt.status) !== 1n || !transferred;
      return { hash, status: confirmations < 12n ? 'pending' : failed ? 'reverted' : 'confirmed', confirmations: String(confirmations) };
    },
    async transaction(id, owner, contract, reference) {
      const chain = await connect(id), asset = networkAsset(chain, contract);
      requireValue(asset && /^0x[0-9a-f]{64}:0x[0-9a-f]+$/i.test(reference));
      const [hash,index] = reference.split(':');
      const receipt = await rpc(chain, 'eth_getTransactionReceipt', [hash]);
      const log = receipt?.logs.find(log => evmSame(log.address,asset.contract) && log.logIndex.toLowerCase() === index.toLowerCase());
      requireValue(log && !log.removed && evmSame(log.transactionHash,hash));
      const decoded = evmToken.parseLog(log);
      requireValue(evmSame(decoded.args.from,owner) || evmSame(decoded.args.to,owner));
      const block = await rpc(chain, 'eth_getBlockByNumber', [receipt.blockNumber,false]);
      requireValue(block && evmSame(block.hash,receipt.blockHash));
      const latest = BigInt(await rpc(chain, 'eth_blockNumber'));
      return { id: reference, transactionHash: hash, contract: asset.contract, from: decoded.args.from, to: decoded.args.to, amountAtomic: String(decoded.args.value), timestamp: new Date(Number(BigInt(block.timestamp))*1000).toISOString(), status: latest-BigInt(receipt.blockNumber)+1n >= 12n ? 'confirmed' : 'pending', incoming: evmSame(decoded.args.to,owner) };
    },
    async history(id, owner, contract, cursor) {
      const chain = await connect(id), asset = networkAsset(chain, contract);
      requireValue(asset && (cursor === undefined || /^(0|[1-9][0-9]{0,14})$/.test(cursor)));
      const latest = BigInt(await rpc(chain, 'eth_blockNumber')), end = cursor === undefined ? latest : BigInt(cursor);
      requireValue(end <= latest);
      const start = end > 1999n ? end - 1999n : 0n, topic = `0x${owner.slice(2).toLowerCase().padStart(64, '0')}`;
      const base = { address: asset.contract, fromBlock: quantity(start), toBlock: quantity(end) };
      const logs = (await Promise.all([[evmToken.getEvent('Transfer').topicHash, topic], [evmToken.getEvent('Transfer').topicHash, null, topic]].map(topics => rpc(chain, 'eth_getLogs', [{ ...base, topics }])))).flat();
      requireValue(logs.length <= 300 && logs.every(log => hashPattern.test(log.transactionHash)));
      const unique = [...new Map(logs.map(log => [`${log.transactionHash}:${log.logIndex}`, log])).values()];
      const transactions = await Promise.all(unique.filter(log => !log.removed).map(async log => {
        requireValue(evmSame(log.address, asset.contract));
        const decoded = evmToken.parseLog(log), block = await rpc(chain, 'eth_getBlockByNumber', [log.blockNumber, false]);
        requireValue(block && evmSame(block.hash, log.blockHash) && (evmSame(decoded.args.from, owner) || evmSame(decoded.args.to, owner)));
        return { id: `${log.transactionHash}:${log.logIndex}`, transactionHash: log.transactionHash, contract: asset.contract, from: decoded.args.from, to: decoded.args.to, amountAtomic: String(decoded.args.value), timestamp: new Date(Number(BigInt(block.timestamp)) * 1000).toISOString(), status: latest - BigInt(log.blockNumber) + 1n >= 12n ? 'confirmed' : 'pending', incoming: evmSame(decoded.args.to, owner), blockNumber: Number(BigInt(log.blockNumber)) };
      }));
      return { transactions: transactions.sort((a,b) => b.blockNumber-a.blockNumber), cursor: start ? String(start-1n) : undefined, hasMore: start > 0n, indexing: { enabled: true, status: start > 0n ? 'backfilling' : 'caught_up' } };
    },
  };
}
