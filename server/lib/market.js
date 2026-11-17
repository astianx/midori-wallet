import { CHART_PERIODS, MARKET_COINS, MARKET_CURRENCIES } from '../../config/market.js';
import { ApiError, requireValue } from './errors.js';

const unavailable = () => new ApiError(503, 'MARKET_UNAVAILABLE', 'Price history is temporarily unavailable');

export function marketService({ apiKey, fetchImpl = globalThis.fetch, now = Date.now } = {}) {
  const cache = new Map();
  const pending = new Map();
  let retryAt = 0;

  async function load(symbol, period, currency) {
    if (now() < retryAt) throw unavailable();
    const url = new URL(`https://api.coingecko.com/api/v3/coins/${MARKET_COINS[symbol]}/market_chart`);
    url.search = new URLSearchParams({ vs_currency: currency.toLowerCase(), days: CHART_PERIODS[period], precision: 'full' });
    try {
      const response = await fetchImpl(url, {
        headers: { Accept: 'application/json', ...(apiKey ? { 'x-cg-demo-api-key': apiKey } : {}) },
        redirect: 'error', signal: AbortSignal.timeout(10000),
      });
      if (!response.ok) throw unavailable();
      const { prices } = await response.json();
      if (!Array.isArray(prices) || prices.length < 2 || prices.length > 10000) throw unavailable();
      let previous = 0;
      const cutoff = now() - (CHART_PERIODS[period] + 1) * 86400000;
      for (const point of prices) {
        if (!Array.isArray(point) || point.length !== 2 || !Number.isSafeInteger(point[0]) || point[0] <= previous || point[0] < cutoff || point[0] > now() + 300000 || !Number.isFinite(point[1]) || point[1] <= 0) throw unavailable();
        previous = point[0];
      }
      if (previous < now() - 86400000) throw unavailable();
      return { symbol, currency, period, source: 'CoinGecko', updatedAt: new Date(previous).toISOString(), prices };
    } catch {
      retryAt = now() + 30000;
      throw unavailable();
    }
  }

  return {
    async chart(symbol, query) {
      const { period = '1D', currency = 'USD' } = query;
      requireValue(Object.keys(query).every(key => ['period', 'currency'].includes(key)) && Object.hasOwn(MARKET_COINS, symbol) && typeof period === 'string' && Object.hasOwn(CHART_PERIODS, period) && MARKET_CURRENCIES.includes(currency), 'Unsupported price history');
      const key = `${symbol}:${period}:${currency}`;
      const saved = cache.get(key);
      if (saved && saved.expires > now()) return saved.data;
      if (pending.has(key)) return pending.get(key);
      const request = load(symbol, period, currency).then(data => {
        cache.set(key, { expires: now() + 300000, data });
        return data;
      }).finally(() => pending.delete(key));
      pending.set(key, request);
      return request;
    },
  };
}
