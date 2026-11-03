import { ApiError } from './errors.js';
import { account } from './repositories.js';

export async function readBalances(db, config, walletId, accountId) {
  const saved = await account(db, walletId, accountId);
  const rpc = async (method, params) => {
    const response = await fetch(config.rpcUrl, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }), signal: AbortSignal.timeout(10000) });
    if (!response.ok) throw new Error();
    const result = await response.json();
    if (result.error || typeof result.result !== 'string' || !/^0x[0-9a-fA-F]+$/.test(result.result)) throw new Error();
    return result.result;
  };
  try {
    if (BigInt(await rpc('eth_chainId', [])) !== 8453n) throw new Error();
    const block = await rpc('eth_blockNumber', []);
    const assets = await Promise.all(saved.assets.map(async asset => ({ chainId: 8453, contract: asset.contract, symbol: asset.symbol, decimals: asset.decimals, address: saved.address, balanceAtomic: BigInt(await rpc('eth_call', [{ to: asset.contract, data: `0x70a08231${saved.address.slice(2).toLowerCase().padStart(64, '0')}` }, block])).toString() })));
    return { accountId, chainId: 8453, address: saved.address, blockNumber: BigInt(block).toString(), readAt: new Date().toISOString(), assets };
  } catch { throw new ApiError(502, 'CHAIN_UNAVAILABLE', 'Could not read balances on Base mainnet'); }
}
