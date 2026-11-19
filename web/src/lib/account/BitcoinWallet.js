import { bitcoinReader } from '@midori/wallet-core';
import { Amount, WalletState, Transaction } from './domain.js';
import MidoriWallet from './MidoriWallet.js';

export default class BitcoinWallet extends MidoriWallet {
  #session;
  constructor(options, descriptor) {
    super(options); this.#session = options.session; this.descriptor = descriptor;
    Object.assign(this, { chainId: 'bitcoin', isWalletConnectSupported: false, isExportSupported: false, isAccountRecoverySupported: true, settings: Object.freeze({ bip84: descriptor.derivationPath }) });
  }
  get address() { return this.descriptor?.address; }
  getPublicKey() { return { address: this.address, derivationPath: this.descriptor.derivationPath }; }
  async load() {
    const generation = this.generation || 0; this.readState = 'loading'; this.state = WalletState.STATE_LOADING;
    try {
      const value = await bitcoinReader(this.address, this.#session.bitcoinClient).getBalance();
      if (value < 0n) throw new Error('Invalid Bitcoin balance');
      if (generation !== (this.generation || 0)) return;
      this.balanceAtomic = value.toString(); this.readAt = new Date().toISOString(); this.readState = 'fresh';
    } catch (error) { if (generation === (this.generation || 0)) this.readState = this.balanceAtomic === undefined ? 'unavailable' : 'stale'; throw error; }
    finally { if (generation === (this.generation || 0)) this.state = WalletState.STATE_LOADED; }
  }
  transaction(item) {
    if (!/^[a-f0-9]{64}$/.test(item.txid) || !Array.isArray(item.vin) || !Array.isArray(item.vout)) throw new Error('Invalid Bitcoin transaction');
    const sum = list => list.reduce((total, entry) => { if (!Number.isSafeInteger(entry.value) || entry.value < 0) throw new Error('Invalid Bitcoin amount'); return total + BigInt(entry.value); }, 0n);
    const spent = sum(item.vin.filter(input => input.prevout?.scriptpubkey_address === this.address).map(input => input.prevout));
    const received = sum(item.vout.filter(output => output.scriptpubkey_address === this.address));
    const incoming = spent === 0n; const fee = BigInt(item.fee || 0);
    const value = incoming ? received : spent - received - fee;
    return { id: item.txid, transactionHash: item.txid, amount: new Amount(value < 0n ? 0n : value, 8), amountAtomic: String(value < 0n ? 0n : value), decimals: 8, incoming, from: item.vin.find(input => input.prevout?.scriptpubkey_address)?.prevout.scriptpubkey_address, to: incoming ? this.address : item.vout.find(output => output.scriptpubkey_address && output.scriptpubkey_address !== this.address)?.scriptpubkey_address || this.address, timestamp: item.status?.block_time ? new Date(item.status.block_time * 1000) : new Date(), chainStatus: item.status?.confirmed ? 'confirmed' : 'pending', status: item.status?.confirmed ? Transaction.STATUS_SUCCESS : Transaction.STATUS_PENDING, action: Transaction.ACTION_TRANSFER, rbf: false, network: 'Bitcoin', networkFee: incoming ? undefined : new Amount(fee, 8), url: `https://mempool.space/tx/${item.txid}` };
  }
  async loadTransactions({ cursor } = {}) {
    if (cursor && !/^[a-f0-9]{64}$/.test(cursor)) throw new Error('Invalid Bitcoin history cursor');
    const data = await this.#session.bitcoinRequest(`address/${this.address}/txs${cursor ? `/chain/${cursor}` : ''}`);
    if (!Array.isArray(data)) throw new Error('Bitcoin history unavailable');
    const confirmed = data.filter(item => item.status?.confirmed);
    return { transactions: data.map(item => this.transaction(item)), cursor: confirmed.at(-1)?.txid, hasMore: confirmed.length >= 25, indexing: { enabled: true, status: 'caught_up' } };
  }
  async loadTransaction(id) { return this.transaction(await this.#session.bitcoinRequest(`tx/${id}`)); }
  pendingBitcoin() { return this.#session.pendingBitcoin(); }
  quoteBitcoin(request) { return this.#session.quoteBitcoin(request); }
  authorizeBitcoin(quote, pinHash, stillApproved) { return this.#session.authorizeBitcoin(quote, pinHash, stillApproved); }
  async reviewRamp(request) { await this.#session.ensureBitcoinRegistration(); return super.reviewRamp(request); }
}
