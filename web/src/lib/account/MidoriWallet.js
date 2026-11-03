import { Amount, WalletState, Transaction } from './domain.js';

export default class MidoriWallet {
  #session;
  constructor({ crypto, platform, session }) {
    this.#session = session;
    Object.assign(this, { crypto, platform, chainId: 8453, state: WalletState.STATE_INITIALIZED, settings: Object.freeze({ bip44: session.account.descriptor.derivationPath }), isAccountRecoverySupported: true, isExportSupported: true, isSettingsSupported: true, isDerivationPathFixed: true, isAddressTypesSupported: false, isAddressChangeSupported: false, addressTypes: [], isWalletConnectSupported: false, isStakingSupported: false, isImportSupported: false });
  }

  get address() { return this.#session.account?.address; }
  get balance() {
    return this.balanceAtomic === undefined ? undefined : new Amount(this.balanceAtomic, this.crypto.decimals);
  }
  resolveRecipient(value) { return this.#session.resolveRecipient(value); }
  reviewPayment(request) { return this.#session.reviewPayment({ ...request, contract: this.crypto.address }); }
  pendingPayments() { return this.#session.payments(); }
  payment(id) { return this.#session.payment(id); }
  cancelPayment(id) { return this.#session.cancelPayment(id); }
  transaction(item) {
    return { ...item, amount: new Amount(item.amountAtomic, item.decimals), timestamp: new Date(item.timestamp), chainStatus: item.status,
      status: item.status === 'confirmed' ? Transaction.STATUS_SUCCESS : ['reorganized','reverted','expired'].includes(item.status) ? Transaction.STATUS_FAILED : Transaction.STATUS_PENDING,
      action: Transaction.ACTION_TRANSFER, rbf: false, url: item.transactionHash ? `https://basescan.org/tx/${item.transactionHash}` : '',
      networkFee: item.actualFeeAtomic == null ? undefined : new Amount(item.actualFeeAtomic, 6),
      maxNetworkFee: item.maxFeeAtomic == null ? undefined : new Amount(item.maxFeeAtomic, 6) };
  }
  async loadTransactions({ cursor } = {}) {
    const data = await this.#session.history(this.crypto.address, cursor);
    return { ...data, transactions: data.transactions.map(item => this.transaction(item)) };
  }
  async loadTransaction(id) {
    const item = await this.#session.client.request('GET', `/api/v1/accounts/${this.#session.account.id}/history/${id}`);
    return this.transaction(item);
  }
  getPublicKey() { return { descriptor: this.#session.account.descriptor }; }
  async open() { this.state = WalletState.STATE_INITIALIZED; }
  async create() { this.state = WalletState.STATE_INITIALIZED; }
  cleanup() { this.generation = (this.generation || 0) + 1; }
  async load() {
    const generation = this.generation || 0;
    this.readState = 'loading';
    this.state = WalletState.STATE_LOADING;
    try {
      const balances = await this.#session.balances();
      const asset = balances.assets.find(asset => asset.contract.toLowerCase() === this.crypto.address);
      if (!asset || typeof asset.balanceAtomic !== 'string') throw new Error('Asset balance unavailable');
      const amount = new Amount(asset.balanceAtomic, this.crypto.decimals);
      if (amount.value < 0n) throw new Error('Invalid balance');
      if (generation !== (this.generation || 0)) return;
      this.balanceAtomic = amount.value.toString();
      this.readAt = balances.readAt;
      this.readState = balances.stale ? 'stale' : 'fresh';
      this.state = WalletState.STATE_LOADED;
    } catch (error) {
      if (generation !== (this.generation || 0)) return;
      this.readState = this.balanceAtomic === undefined ? 'unavailable' : 'stale';
      this.state = WalletState.STATE_LOADED;
      throw error;
    }
  }
}
