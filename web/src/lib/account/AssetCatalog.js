export const BASE_ASSETS = Object.freeze([
  Object.freeze({ symbol: 'USDT', contract: '0xfde4c96c8593536e31f229ea8f37b2ada2699bb2', decimals: 6, representation: 'Bridged USDT' }),
  Object.freeze({ symbol: 'USDC', contract: '0x833589fcd6edb6e08f4c7c32d4f71b54bda02913', decimals: 6, representation: 'Circle native USDC' }),
]);
export const basePlatform = Object.freeze({ _id: 'base@base', asset: 'base', platform: 'base', type: 'coin', symbol: 'ETH', name: 'Base', decimals: 18, supported: false, meta: {} });
export function validateAssets(assets) {
  if (!Array.isArray(assets) || assets.length !== 2 || !BASE_ASSETS.every(expected => assets.filter(asset => asset.symbol === expected.symbol && asset.contract?.toLowerCase() === expected.contract && asset.decimals === 6).length === 1)) throw new Error('Unexpected Base asset registry');
  return BASE_ASSETS.map(asset => Object.freeze({ ...asset, address: asset.contract, _id: `${asset.contract}@base`, asset: asset.symbol === 'USDC' ? 'usd-coin' : 'tether', platform: 'base', type: 'token', name: asset.symbol === 'USDC' ? 'USD Coin' : 'Tether USD', chainId: 8453, supported: true, meta: {} }));
}
export default class AssetCatalog {
  constructor(assets = validateAssets(BASE_ASSETS)) { this.all = [...assets, basePlatform]; this.new = []; this.popular = assets; }
  async init() {}
  get(id) { return this.all.find(asset => asset._id === id); }
  platform(name) { return name === 'base' ? basePlatform : undefined; }
  platforms(names) { return names.includes('base') ? [basePlatform] : []; }
  getTokenByAddress(platform, address) { return platform === 'base' ? this.all.find(asset => asset.address === address.toLowerCase()) : undefined; }
}
