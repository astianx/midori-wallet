import { isStablecoin, stablecoinNetwork } from '../../../../config/stablecoin-networks.js';
import { Amount } from './domain.js';

export function stablecoinFamily(crypto) {
  return crypto?.type === 'token' && stablecoinNetwork(crypto.platform) && isStablecoin(crypto.symbol) ? crypto.symbol === 'USDC' ? 'USDC' : 'USDT' : undefined;
}
export function stablecoinPositions(cryptos, catalog) {
  const families = new Set(cryptos.map(stablecoinFamily).filter(Boolean));
  return [...new Map([...cryptos, ...catalog.filter(crypto => crypto.addable !== false && families.has(stablecoinFamily(crypto)))].map(crypto => [crypto._id, crypto])).values()];
}
export function canonicalStablecoin(catalog, family) {
  return catalog.find(crypto => stablecoinFamily(crypto) === family && crypto.platform === (family === 'USDC' ? 'base' : 'ethereum'));
}
export function groupStablecoins(items, activeId) {
  const groups = new Map(), result = [];
  for (const item of items) {
    const family = stablecoinFamily(item.crypto);
    if (!family) { result.push(item); continue; }
    if (!groups.has(family)) { const group = { family, positions: [] }; groups.set(family, group); result.push(group); }
    groups.get(family).positions.push(item);
  }
  return result.map(group => {
    if (!group.family) return group;
    const positions = group.positions, preferred = group.family === 'USDC' ? 'base' : 'ethereum';
    const item = positions.find(item => item.crypto._id === activeId) || positions.find(item => item.crypto.platform === preferred) || positions[0];
    const known = positions.every(item => item.balance && item.balance.value >= 0n);
    const decimals = Math.max(...positions.map(item => item.crypto.decimals));
    const balance = known ? new Amount(positions.reduce((sum, item) => sum + item.balance.value * 10n ** BigInt(decimals - item.balance.decimals), 0n), decimals) : undefined;
    return { ...item, ...group, crypto: { ...item.crypto, symbol: group.family, name: group.family === 'USDC' ? 'USD Coin' : 'Tether USD' }, title: group.family, subtitle: group.family === 'USDC' ? 'USD Coin' : 'Tether USD', balance, balanceRound: balance, balanceFiat: undefined, readState: known ? positions.every(item => item.readState === 'fresh') ? 'fresh' : 'stale' : 'unavailable' };
  });
}
