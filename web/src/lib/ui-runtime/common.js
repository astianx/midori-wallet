export { default as Amount } from './vendor/Amount.js';
export { default as Transaction } from './vendor/Transaction.js';
export * as errors from './vendor/errors.js';
export * as utils from './vendor/utils.js';

export class WalletIntegrationPendingError extends Error {
  name = 'WalletIntegrationPendingError';
  constructor() { super('Wallet engine integration is pending. The restored interface does not enable legacy Midori Wallet engines.'); }
}
export class MwWallet {
  static STATE_CREATED = Symbol('CREATED');
  static STATE_NEED_INITIALIZATION = Symbol('NEED_INITIALIZATION');
  static STATE_INITIALIZING = Symbol('INITIALIZING');
  static STATE_INITIALIZED = Symbol('INITIALIZED');
  static STATE_LOADING = Symbol('LOADING');
  static STATE_LOADED = Symbol('LOADED');
  static STATE_ERROR = Symbol('ERROR');
  static ADDRESS_TYPE_BASE = Symbol('base');
  static ADDRESS_TYPE_UNKNOWN = Symbol('unknown');
  static FEE_RATE_DEFAULT = Symbol('default');
  constructor() { throw new WalletIntegrationPendingError(); }
}
