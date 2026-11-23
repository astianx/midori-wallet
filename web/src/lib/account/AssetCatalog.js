import { evmNetworks } from '../../../../config/stablecoin-networks.js';
import { MARKET_COINS } from '../../../../config/market.js';

export const BASE_ASSETS = Object.freeze([
  Object.freeze({ symbol: 'USDT', contract: '0xfde4c96c8593536e31f229ea8f37b2ada2699bb2', decimals: 6, representation: 'Bridged USDT' }),
  Object.freeze({ symbol: 'USDC', contract: '0x833589fcd6edb6e08f4c7c32d4f71b54bda02913', decimals: 6, representation: 'Circle native USDC' }),
]);
export const basePlatform = Object.freeze({ _id: 'base@base', asset: 'base', platform: 'base', type: 'coin', symbol: 'ETH', name: 'Base', decimals: 18, supported: false, meta: {} });
export function validateAssets(assets) {
  if (!Array.isArray(assets) || assets.length !== 2 || !BASE_ASSETS.every(expected => assets.filter(asset => asset.symbol === expected.symbol && asset.contract?.toLowerCase() === expected.contract && asset.decimals === 6).length === 1)) throw new Error('Unexpected Base asset registry');
  return BASE_ASSETS.map(asset => Object.freeze({ ...asset, address: asset.contract, _id: `${asset.contract}@base`, asset: MARKET_COINS[asset.symbol], marketId: MARKET_COINS[asset.symbol], platform: 'base', type: 'token', name: asset.symbol === 'USDC' ? 'USD Coin' : 'Bridged USDT', chainId: 8453, addable: asset.symbol === 'USDC', supported: true, meta: {} }));
}
export const bitcoinAsset = Object.freeze({ _id: 'bitcoin@bitcoin', asset: 'bitcoin', marketId: 'bitcoin', platform: 'bitcoin', type: 'coin', symbol: 'BTC', name: 'Bitcoin', decimals: 8, supported: true, meta: {} });
export const sparkAsset = Object.freeze({ ...bitcoinAsset, _id: 'bitcoin@spark', platform: 'spark', name: 'Bitcoin · Spark / Lightning' });
export const evmPlatforms = evmNetworks.map(network => Object.freeze({ _id: `${network.id}@${network.id}`, platform: network.id, type: 'coin', symbol: network.nativeSymbol, name: network.name, decimals: 18, supported: false, meta: {} }));
export const evmAssets = evmNetworks.flatMap(network => network.assets.map(asset => Object.freeze({ ...asset, _id: `${asset.contract}@${network.id}`, address: asset.contract, platform: network.id, type: 'token', name: asset.symbol === 'USDC' ? 'USD Coin' : asset.symbol === 'USDT0' ? 'Tether USD · USDT0' : 'Tether USD', chainId: network.chainId, supported: true, addable: true, asset: asset.symbol === 'USDC' ? 'usd-coin' : 'tether', marketId: asset.symbol === 'USDC' ? 'usd-coin' : 'tether', meta: {} })));
export default class AssetCatalog {
  constructor(assets = validateAssets(BASE_ASSETS)) { this.all = [...assets, ...evmAssets, bitcoinAsset, sparkAsset, basePlatform, ...evmPlatforms]; this.new = []; this.popular = assets.filter(asset => asset.addable); }
  async init() {}
  get(id) { return this.all.find(asset => asset._id === id); }
  platform(name) { return name === 'base' ? basePlatform : name === 'bitcoin' ? bitcoinAsset : name === 'spark' ? sparkAsset : evmPlatforms.find(item => item.platform === name); }
  platforms(names) { return [basePlatform, bitcoinAsset, sparkAsset, ...evmPlatforms].filter(item => names.includes(item.platform)); }
  getTokenByAddress(platform, address) { return this.all.find(asset => asset.platform === platform && asset.address === address?.toLowerCase()); }
}
