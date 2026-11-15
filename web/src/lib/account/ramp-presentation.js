import { Amount } from './domain.js';

export const isPendingRampOrder = order => ['created', 'awaiting_payment', 'processing'].includes(order.status);
export function rampAmount(value, currency) {
  if (value == null || !currency) return '—';
  try { return `${Amount.fromString(String(value), 18).toString()} ${currency}`; } catch { return '—'; }
}
export function rampStatus(order, t) {
  return t({ created: 'Started', awaiting_payment: 'Awaiting payment', processing: 'Processing', completed: 'Completed', failed: 'Failed', refunded: 'Refunded', expired: 'Expired' }[order.status] || 'Unavailable');
}
export function rampStatusMessage(order, t) {
  return t(order.status === 'completed' ? 'Your wallet balance updates separately.' : order.status === 'refunded' ? 'Check your original payment method.' : 'We have not confirmed completion. Check the latest order status.');
}
export const rampNetwork = environment => environment === 'STAGING' ? 'Base Sepolia' : environment === 'PRODUCTION' ? 'Base' : '—';
export function rampAssetSupported(options, asset, kind) {
  if (!options?.products?.includes(kind.toUpperCase()) || options.network !== 'base' || !asset?.[kind]) return false;
  if (options.environment === 'STAGING') return asset.symbol === 'USDC' && String(asset.chainId) === '84532';
  return options.environment === 'PRODUCTION' && String(asset.chainId) === '8453' && asset.decimals === 6;
}
