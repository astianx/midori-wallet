import { Amount, WalletState, Transaction } from './domain.js';
import MidoriWallet from './MidoriWallet.js';
import { stablecoinNetwork } from '../../../../config/stablecoin-networks.js';

export default class EvmWallet extends MidoriWallet {
  #session;
  constructor(options) {
    super(options); this.#session = options.session;
    this.network = stablecoinNetwork(this.crypto.platform);
    Object.assign(this, { chainId: this.network.chainId, isEvm: true, isWalletConnectSupported: false, isExportSupported: false, isSettingsSupported: false });
  }
  get address() { return this.#session.descriptor?.ownerAddress; }
  getPublicKey() { return { address: this.address, chainId: this.chainId }; }
  request(action, data) { return this.#session.evmRequest(this.network.id, action, data); }
  quoteEvm(request) { return this.request('quote', { ...request, contract: this.crypto.address }); }
  retryEvm(hash) { return this.#session.retryEvm(this.network.id, hash); }
  pendingEvm() { return this.#session.pendingEvm(this.network.id); }
  async load() {
    const generation = this.generation || 0; this.readState = 'loading'; this.state = WalletState.STATE_LOADING;
    try {
      const data = await this.request('balance', { contract: this.crypto.address });
      if (data.chainId !== this.chainId || data.address.toLowerCase() !== this.address.toLowerCase() || data.contract.toLowerCase() !== this.crypto.address) throw new Error('Unexpected balance');
      const balance = new Amount(data.balanceAtomic, this.crypto.decimals), native = new Amount(data.nativeBalanceAtomic, 18);
      if (balance.value < 0n || native.value < 0n) throw new Error('Invalid balance');
      if (generation !== (this.generation || 0)) return;
      this.balanceAtomic = balance.value.toString(); this.nativeBalance = native; this.readState = 'fresh'; this.readAt = data.readAt;
    } catch (error) { if (generation === (this.generation || 0)) this.readState = this.balanceAtomic === undefined ? 'unavailable' : 'stale'; throw error; }
    finally { if (generation === (this.generation || 0)) this.state = WalletState.STATE_LOADED; }
  }
  transaction(item) {
    return { ...item, chainId: this.chainId, symbol: this.crypto.symbol, decimals: this.crypto.decimals, amount: new Amount(item.amountAtomic, this.crypto.decimals), timestamp: new Date(item.timestamp), chainStatus: item.status, status: item.status === 'confirmed' ? Transaction.STATUS_SUCCESS : item.status === 'reverted' ? Transaction.STATUS_FAILED : Transaction.STATUS_PENDING, action: Transaction.ACTION_TRANSFER, rbf: false, network: this.network.name, receiptSupported: false, url: `${this.network.explorer}/tx/${item.transactionHash}` };
  }
  async loadTransactions({ cursor } = {}) { const data = await this.request('history', { contract: this.crypto.address, ...(cursor === undefined ? {} : { cursor }) }); return { ...data, transactions: data.transactions.map(item => this.transaction(item)) }; }
  async loadTransaction(id) { return this.transaction(await this.request('transaction', { contract: this.crypto.address, reference: id })); }
}
