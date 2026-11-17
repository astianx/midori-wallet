import { CHART_PERIODS, MARKET_CURRENCIES } from '../../../../config/market.js';

export default class AccountMarket {
  constructor(session) { this.session = session; }
  async init() {}
  async getMarket() { return undefined; }
  async getPrice() { return undefined; }
  async getChartData(id, period, currency) {
    const asset = this.session.assets?.find(asset => asset._id === id && asset.marketId);
    if (!asset || !Object.hasOwn(CHART_PERIODS, period) || !MARKET_CURRENCIES.includes(currency)) throw new Error('Unsupported price history');
    const query = new URLSearchParams({ period, currency });
    return this.session.publicClient.request('GET', `/api/v1/market/${asset.symbol}/chart?${query}`);
  }
}
