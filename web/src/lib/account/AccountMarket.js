import { bitcoinAsset, sparkAsset } from './AssetCatalog.js';
import { CHART_PERIODS, MARKET_CURRENCIES } from '../../../../config/market.js';

export default class AccountMarket {
  constructor(session) { this.session = session; this.cache = new Map(); this.pending = new Map(); }
  async init() {}
  async getMarket(id, currency = 'USD') {
    if (id === sparkAsset._id) id = bitcoinAsset._id;
    if (id !== bitcoinAsset._id) return undefined;
    const key = `${id}:${currency}`, cached = this.cache.get(key);
    if (cached?.expires > Date.now()) return cached.market;
    if (!this.pending.has(key)) this.pending.set(key, this.getChartData(id, '1D', currency).then(async data => {
      const first = data.prices[0][1], price = data.prices.at(-1)[1];
      const usdPrice = currency === 'USD' ? price : (await this.getMarket(id, 'USD'))?.price;
      const market = { price, usdPrice, currency, change: (price - first) / first, source: data.source, updatedAt: data.updatedAt };
      this.cache.set(key, { market, expires: Date.now() + 300000 }); return market;
    }).catch(() => undefined).finally(() => this.pending.delete(key)));
    return this.pending.get(key);
  }
  async getPrice(id, currency) { return (await this.getMarket(id, currency))?.price; }
  async getChartData(id, period, currency) {
    const asset = [...(this.session.assets || []), bitcoinAsset, sparkAsset].find(asset => asset._id === id && asset.marketId);
    if (!asset || !Object.hasOwn(CHART_PERIODS, period) || !MARKET_CURRENCIES.includes(currency)) throw new Error('Unsupported price history');
    const query = new URLSearchParams({ period, currency });
    return this.session.publicClient.request('GET', `/api/v1/market/${asset.symbol}/chart?${query}`);
  }
}
