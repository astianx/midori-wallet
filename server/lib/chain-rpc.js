import { ApiError } from './errors.js';

export function chainRpc(config, fetchImpl = fetch) {
  return async (method, params = []) => {
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
