import { sparkTools } from '@midori/wallet-core';
import { Amount, WalletState, Transaction } from './domain.js';
import MidoriWallet from './MidoriWallet.js';

export default class SparkWallet extends MidoriWallet {
  #session;
  constructor(options, descriptor) {
    super(options); options.session.hasSparkWallet = true; this.#session = options.session; this.descriptor = descriptor;
    Object.assign(this, { chainId: 'spark', isWalletConnectSupported: false, isExportSupported: false, settings: Object.freeze({ bip44: descriptor.derivationPath }) });
  }
  get address() { return this.descriptor?.address; }
  getPublicKey() { return { ...this.descriptor }; }
  async load() {
    const generation = this.generation || 0; this.readState = 'loading'; this.state = WalletState.STATE_LOADING;
    try {
      const value = await this.#session.spark.read('balance');
      if (typeof value !== 'bigint' || value < 0n) throw new Error('Invalid Spark balance');
      if (generation !== (this.generation || 0)) return;
      this.balanceAtomic = String(value); this.readAt = new Date().toISOString(); this.readState = 'fresh';
    } catch (error) { if (generation === (this.generation || 0)) this.readState = this.balanceAtomic === undefined ? 'unavailable' : 'stale'; throw error; }
    finally { if (generation === (this.generation || 0)) this.state = WalletState.STATE_LOADED; }
  }
  async transaction(transfer) {
    const { normalizeSparkTransfer } = await sparkTools();
    const item = normalizeSparkTransfer(transfer, this.descriptor.identityKey);
    const complete = item.status === 'COMPLETED', failed = ['EXPIRED','RETURNED','CANCELLED'].includes(item.status);
    return { ...item, amount: new Amount(item.amountAtomic, 8), decimals: 8, transactionHash: item.id, timestamp: new Date(item.timestamp), chainStatus: complete ? 'confirmed' : failed ? 'reverted' : 'pending', status: complete ? Transaction.STATUS_SUCCESS : failed ? Transaction.STATUS_FAILED : Transaction.STATUS_PENDING, action: Transaction.ACTION_TRANSFER, network: 'Spark / Lightning', rbf: false, url: '' };
  }
  async loadTransactions({ cursor } = {}) {
    const skip = cursor === undefined ? 0 : Number(cursor);
    if (!Number.isSafeInteger(skip) || skip < 0 || skip > 100000) throw new Error('Invalid Spark cursor');
    const transfers = await this.#session.spark.read('transfers', { limit: 20, skip });
    return { transactions: await Promise.all(transfers.map(item => this.transaction(item))), cursor: String(skip + transfers.length), hasMore: transfers.length === 20, indexing: { enabled: true, status: 'caught_up' } };
  }
  async loadTransaction(id) { return this.transaction((await this.#session.spark.read('transaction', id)).transfer); }
  sparkRead(method, value) { return this.#session.spark.read(method, value); }
  pendingSpark() { return this.#session.spark.pending(); }
  quoteSpark(value) { return this.#session.spark.quote(value); }
  createLightningInvoice(amount, memo) { return this.#session.spark.invoice(amount, memo); }
  lightningInvoices() { return this.#session.spark.invoices(); }
  async exchangeOptions() { await this.#session.spark.register(); return super.exchangeOptions(); }
}
