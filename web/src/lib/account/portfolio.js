import Big from 'big.js';

export function portfolioReferenceValue(assets) {
  if (!assets?.length) return null;
  let total = new Big(0);
  for (const asset of assets) {
    if (!asset.balance || asset.balance.value < 0n) return null;
    const price = asset.crypto.symbol === 'BTC' ? asset.market?.usdPrice : ['USDC', 'USDT', 'USDT0'].includes(asset.crypto.symbol) ? 1 : undefined;
    if (!Number.isFinite(price) || price <= 0) return null;
    total = total.plus(new Big(asset.balance.toString()).times(price));
  }
  return total.toFixed(2);
}
