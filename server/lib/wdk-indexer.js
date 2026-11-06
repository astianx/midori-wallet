import { ApiError } from './errors.js';

export function loadWdkIndexerConfig(env = process.env) {
  const baseUrl = env.WDK_INDEXER_BASE_URL || 'https://wdk-api.tether.io';
  let url;
  try { url = new URL(baseUrl); } catch { throw new Error('Invalid WDK_INDEXER_BASE_URL'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw new Error('WDK_INDEXER_BASE_URL must be an HTTPS origin without /api/v1');
  const apiKey = env.WDK_INDEXER_API_KEY?.trim() || '';
  if (apiKey.length > 4096 || /[^\x21-\x7e]/.test(apiKey)) throw new Error('Invalid WDK_INDEXER_API_KEY');
  const timeoutMs = Number(env.WDK_INDEXER_TIMEOUT_MS || 30000);
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1000 || timeoutMs > 60000) throw new Error('Invalid WDK_INDEXER_TIMEOUT_MS');
  return Object.freeze({ baseUrl: url.origin, apiKey, timeoutMs });
}

export function createWdkIndexer(config, fetchImpl = fetch) {
  const invalid = () => new ApiError(502, 'WDK_INDEXER_INVALID_RESPONSE', 'WDK Indexer returned an invalid response');
  const request = async (path, authenticated) => {
    if (authenticated && !config.apiKey) throw new ApiError(503, 'WDK_INDEXER_NOT_CONFIGURED', 'Set WDK_INDEXER_API_KEY on the server');
    const signal = AbortSignal.timeout(config.timeoutMs);
    try {
      const response = await fetchImpl(`${config.baseUrl}/api/v1${path}`, {
        method: 'GET', redirect: 'error', signal,
        headers: { Accept: 'application/json', ...(authenticated ? { 'x-api-key': config.apiKey } : {}) },
      });
      if (response.status === 401 || response.status === 403) throw new ApiError(502, 'WDK_INDEXER_ACCESS_DENIED', 'WDK Indexer rejected access', { upstreamStatus: response.status });
      if (response.status === 429) {
        const retry = response.headers.get('retry-after');
        const seconds = /^\d+$/.test(retry || '') ? Number(retry) : (Date.parse(retry) - Date.now()) / 1000;
        throw new ApiError(503, 'WDK_INDEXER_RATE_LIMITED', 'WDK Indexer request limit reached', { retryAfter: Math.min(3600, Math.max(1, Math.ceil(seconds) || 60)) });
      }
      if (!response.ok) throw new ApiError(502, 'WDK_INDEXER_UNAVAILABLE', 'WDK Indexer is unavailable', { upstreamStatus: response.status });
      return await response.json();
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (signal.aborted) throw new ApiError(504, 'WDK_INDEXER_TIMEOUT', 'WDK Indexer request timed out');
      if (error instanceof SyntaxError) throw invalid();
      throw new ApiError(502, 'WDK_INDEXER_UNAVAILABLE', 'Could not reach WDK Indexer');
    }
  };
  return Object.freeze({
    async getChains() {
      const body = await request('/chains', false);
      const slug = value => typeof value === 'string' && /^[a-z0-9][a-z0-9_-]{0,63}$/.test(value);
      if (!Array.isArray(body?.chains) || body.chains.some(chain => !chain || !slug(chain.name) || !Array.isArray(chain.tokens) || !chain.tokens.every(slug))) throw invalid();
      return body.chains;
    },
    async verifyAccess() {
      const body = await request('/wallets', true);
      if (!Array.isArray(body?.wallets)) throw invalid();
      return true;
    },
  });
}

export async function inspectWdkIndexer(config, fetchImpl = fetch) {
  if (!config.apiKey) return { configured: false, authenticated: false, historyProvider: 'rpc' };
  const client = createWdkIndexer(config, fetchImpl);
  const chains = await client.getChains();
  await client.verifyAccess();
  const requiredTokens = ['usdt', 'usdc'];
  const tokens = chains.find(chain => chain.name === 'base')?.tokens || [];
  const unsupportedTokens = requiredTokens.filter(token => !tokens.includes(token));
  return {
    configured: true, authenticated: true, baseUrl: config.baseUrl,
    network: 'base', requiredTokens, supportedTokens: tokens, unsupportedTokens,
    coverageComplete: unsupportedTokens.length === 0, historyProvider: 'rpc', chains,
  };
}
