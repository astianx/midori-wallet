import { ApiError } from './errors.js';

// Public/shared Base RPC endpoints (e.g. mainnet.base.org) enforce a hard
// per-second request cap (observed: 5 req/s) and reject bursts with 429 or
// JSON-RPC -32011, regardless of how small each individual call is. The
// indexer issues several sequential calls per account per tick (getLogs x2,
// getBlockByNumber, getTransactionReceipt...); without pacing them it blows
// through that cap on the very first tick. Space calls from a single client
// instance so a whole synchronizeHistory() pass (all accounts) stays under
// the limit, leaving headroom for the concurrent payment-reconciliation RPC
// traffic sharing the same endpoint/IP.
const MIN_CALL_INTERVAL_MS = 500;

export function chainRpc(config, fetchImpl = fetch) {
  let nextCallAt = 0;
  return async (method, params = []) => {
    const wait = nextCallAt - Date.now();
    if (wait > 0) await new Promise(resolve => setTimeout(resolve, wait));
    nextCallAt = Date.now() + MIN_CALL_INTERVAL_MS;
    try {
      const response = await fetchImpl(config.rpcUrl, { redirect: 'error', method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }), signal: AbortSignal.timeout(15000) });
      if (response.status === 429) {
        const retry = response.headers.get('retry-after');
        const seconds = /^\d+$/.test(retry || '') ? Number(retry) : (Date.parse(retry) - Date.now()) / 1000;
        throw new ApiError(503, 'CHAIN_RATE_LIMITED', 'Base history synchronization is waiting for the RPC request limit to reset', { retryAfter: Math.min(3600, Math.max(300, Math.ceil(seconds) || 300)) });
      }
      if (!response.ok) throw new Error();
      const body = await response.json();
      if (body.error?.code === -32011) throw new ApiError(503, 'CHAIN_RATE_LIMITED', 'Base history synchronization is waiting for the RPC request limit to reset', { retryAfter: 300 });
      if (body.error || body.result == null) throw new Error();
      return body.result;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(502, 'CHAIN_UNAVAILABLE', 'Could not synchronize Base; the last indexed movements were preserved');
    }
  };
}
