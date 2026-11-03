export * as errors from '../ui-runtime/vendor/errors.js';
export * as utils from '../ui-runtime/vendor/utils.js';
export { default as Transaction } from '../ui-runtime/vendor/Transaction.js';

export const UINT256_MAX = (1n << 256n) - 1n;
export class AccountError extends Error {
  constructor(code, message = code) { super(message); this.name = 'AccountError'; this.code = code; }
}
export class Amount {
  constructor(value, decimals = 6) {
    if (typeof value === 'number' || !/^-?[0-9]+$/.test(String(value)) || !Number.isInteger(decimals) || decimals < 0 || decimals > 36) throw new AccountError('INVALID_AMOUNT');
    this.value = BigInt(value); this.decimals = decimals;
    if (this.value > UINT256_MAX || this.value < -UINT256_MAX) throw new AccountError('AMOUNT_OVERFLOW');
    Object.freeze(this);
  }
  toString() {
    const value = this.value < 0n ? -this.value : this.value;
    const digits = value.toString().padStart(this.decimals + 1, '0');
    const fraction = this.decimals ? digits.slice(-this.decimals).replace(/0+$/, '') : '';
    return `${this.value < 0n ? '-' : ''}${this.decimals ? digits.slice(0, -this.decimals) : digits}${fraction ? '.' + fraction : ''}`;
  }
  static fromString(text, decimals = 6) {
    if (typeof text !== 'string' || !/^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(text)) throw new AccountError('INVALID_AMOUNT');
    const [whole, fraction = ''] = text.replace(/^-/, '').split('.');
    if (fraction.length > decimals) throw new AccountError('EXCESS_PRECISION');
    return new Amount(`${text.startsWith('-') ? '-' : ''}${whole}${fraction.padEnd(decimals, '0')}`, decimals);
  }
}
export const WalletState = Object.freeze({ ...Object.fromEntries(['CREATED','NEED_INITIALIZATION','INITIALIZING','INITIALIZED','LOADING','LOADED','ERROR'].map(name => [`STATE_${name}`, name])), ADDRESS_TYPE_BASE: 'base', ADDRESS_TYPE_UNKNOWN: 'unknown', FEE_RATE_DEFAULT: 'default' });
export function transferAmount(text) {
  const amount = Amount.fromString(text, 6);
  if (amount.value <= 0n) throw new AccountError('NON_POSITIVE_AMOUNT');
  return amount;
}
export function recipient(address, chainId = 8453) {
  if (chainId !== 8453 || !/^0x[0-9a-fA-F]{40}$/.test(address) || /^0x0{40}$/i.test(address)) throw new AccountError('INVALID_RECIPIENT');
  return Object.freeze({ address, chainId });
}
export function quote({ asset, recipient: destination, amountAtomic, feeAtomic, feeAsset, expiresAt }) {
  if (!asset || asset.chainId !== 8453 || !['USDT','USDC'].includes(asset.symbol) || !feeAsset || feeAsset.chainId !== 8453 || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) throw new AccountError('INVALID_QUOTE');
  const amount = new Amount(amountAtomic); const fee = new Amount(feeAtomic);
  if (amount.value <= 0n || fee.value < 0n) throw new AccountError('INVALID_QUOTE');
  return Object.freeze({ asset, recipient: recipient(destination.address, destination.chainId), amountAtomic: amount.value.toString(), feeAtomic: fee.value.toString(), feeAsset, expiresAt });
}
export function isUnavailable(error) { return ['NETWORK_UNCONFIRMED','CHAIN_UNAVAILABLE'].includes(error?.code) || error?.status >= 500; }
