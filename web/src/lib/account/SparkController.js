import { createSparkSession, parseTokenAmount, sparkTools } from '@midori/wallet-core';

const fail = code => Object.assign(new Error(code), { code });
const MAX_SATS = 2100000000000000n;
const satoshis = value => { if (typeof value !== 'string' || !/^[0-9]+$/.test(value) || BigInt(value) <= 0n || BigInt(value) > MAX_SATS) throw fail('INVALID_REQUEST'); return BigInt(value); };
export default class SparkController {
  #active; #registration; #busy = false; #epoch = 0;
  constructor(options) { this.options = options; }
  async session() {
    const epoch = this.#epoch;
    if (!this.options.unlocked()) throw fail('ACCOUNT_LOCKED');
    if (this.options.online?.() === false) throw fail('NETWORK_UNCONFIRMED');
    if (!this.#active) this.#active = (async () => {
      const seed = await this.options.seed();
      try {
        const session = await createSparkSession(seed);
        if (epoch !== this.#epoch || !this.options.unlocked()) { await session.dispose(); throw fail('ACCOUNT_LOCKED'); }
        return session;
      } finally { seed.fill(0); }
    })().catch(error => { if (epoch === this.#epoch) this.#active = null; throw error; });
    return this.#active;
  }
  async descriptor() {
    const epoch = this.#epoch;
    if (!this.options.unlocked()) throw fail('ACCOUNT_LOCKED');
    const cached = this.options.cachedDescriptor?.();
    if (cached) {
      const { SPARK_PATH, SPARK_NETWORK, sparkReader } = await sparkTools();
      if (cached.derivationPath !== SPARK_PATH || cached.network !== SPARK_NETWORK || await sparkReader(cached.address).getIdentityKey() !== cached.identityKey) throw fail('ACCOUNT_RECOVERY_FAILED');
      if (epoch !== this.#epoch || !this.options.unlocked()) throw fail('ACCOUNT_LOCKED');
      return Object.freeze({ ...cached });
    }
    return (await this.session()).descriptor;
  }
  async read(method, value) {
    if (!['balance','transfers','transaction','deposit','deposits','utxos','invoiceStatus','paymentStatus','withdrawStatus'].includes(method)) throw fail('CAPABILITY_UNAVAILABLE');
    const epoch = this.#epoch, session = await this.session();
    const result = await session.read(method, value);
    if (epoch !== this.#epoch) throw fail('ACCOUNT_LOCKED');
    return result;
  }
  async key() { return `_midori_spark_${(await this.descriptor()).address}`; }
  async pending() {
    const key = await this.key(), pending = this.options.readLocal(key);
    if (!pending) return null;
    const hash = pending.hash || pending.id;
    try {
      const method = pending.kind === 'lightning' && pending.hash ? 'paymentStatus' : pending.kind === 'withdraw' && pending.hash ? 'withdrawStatus' : 'transaction';
      const data = await this.read(method, hash);
      const settled = method === 'paymentStatus' ? ['TRANSFER_COMPLETED','USER_SWAP_RETURNED'].includes(data?.status) : method === 'withdrawStatus' ? data?.status === 'SUCCEEDED' : ['final','dropped'].includes(data?.finality);
      if (settled) { this.options.writeLocal(key, null); return null; }
    } catch { /* Missing or interrupted reads leave the durable payment pending. */ }
    return pending;
  }
  async register() {
    const owner = this.options.owner();
    if (!owner.walletId || !owner.accountId) throw fail('NETWORK_UNCONFIRMED');
    if (!this.#registration) this.#registration = (async () => {
      const session = await this.session(), descriptor = session.descriptor;
      const depositAddress = await this.read('deposit');
      const { sparkBindingMessage } = await sparkTools();
      const message = sparkBindingMessage(owner.walletId, owner.accountId, descriptor.address, depositAddress);
      const body = { address: descriptor.address, depositAddress, signature: await session.read('signBinding', message), ownerSignature: await this.options.signOwner(message) };
      const result = await this.options.request('PUT', `/api/v1/accounts/${owner.accountId}/spark`, body);
      if (result.address !== descriptor.address || result.depositAddress !== depositAddress) throw fail('ACCOUNT_RECOVERY_FAILED');
      return result;
    })().catch(error => { this.#registration = null; throw error; });
    return this.#registration;
  }
  async invoice(amountAtomic, memo = '') {
    const value = satoshis(amountAtomic);
    if (typeof memo !== 'string' || memo.length > 140) throw fail('INVALID_REQUEST');
    const session = await this.session();
    const raw = await session.read('invoice', { amountSats: Number(value), memo });
    const invoice = typeof raw.invoice === 'string' ? raw.invoice : raw.invoice?.encodedInvoice || raw.invoice?.paymentRequest;
    const { lightningDetails } = await sparkTools();
    const decoded = lightningDetails(invoice);
    if (decoded.amountAtomic !== amountAtomic || typeof raw.id !== 'string') throw fail('INVALID_INVOICE');
    const item = { ...decoded, id: raw.id, createdAt: new Date().toISOString() };
    const key = (await this.key()) + '_invoices';
    const items = this.options.readLocal(key) || [];
    this.options.writeLocal(key, [item, ...items].slice(0, 50));
    return item;
  }
  async invoices() { return this.options.readLocal((await this.key()) + '_invoices') || []; }
  async quote({ kind, to = '', invoice, amountAtomic, transactionId, exchangeOrderId }) {
    if (this.#busy || await this.pending()) throw fail('PAYMENT_ALREADY_PENDING');
    const session = await this.session();
    const { validateSparkAddress, lightningDetails } = await sparkTools();
    let amount, fee = 0n, details = {};
    if (kind === 'claim') {
      if (!/^[a-f0-9]{64}$/.test(transactionId || '')) throw fail('INVALID_REQUEST');
      const deposit = await this.read('deposit'), utxos = await this.read('utxos', { depositAddress: deposit, excludeClaimed: true });
      if (utxos.utxos.filter(item => item.txid === transactionId).length !== 1) throw fail('INVALID_REQUEST');
      const credit = await session.read('claimQuote', transactionId);
      const transaction = await this.options.bitcoin(`tx/${transactionId}`);
      if (!transaction.status?.confirmed) throw fail('QUOTE_UNAVAILABLE');
      const outputs = transaction.vout.filter(item => item.scriptpubkey_address === deposit);
      if (outputs.length !== 1) throw fail('QUOTE_UNAVAILABLE');
      const deposited = outputs.reduce((total, item) => total + BigInt(item.value), 0n);
      amount = BigInt(credit.creditAmountSats);
      if (amount > deposited || amount <= 0n) throw fail('QUOTE_UNAVAILABLE');
      fee = deposited - amount; details = { transactionId, signature: credit.signature, depositedAtomic: String(deposited) };
    } else {
      amount = satoshis(amountAtomic);
      if (kind === 'spark') { if (!validateSparkAddress(to) || to === session.descriptor.address) throw fail('INVALID_RECIPIENT'); }
      else if (kind === 'lightning') {
        const decoded = lightningDetails(invoice);
        if (decoded.amountAtomic && BigInt(decoded.amountAtomic) !== amount) throw fail('PAYMENT_CHANGED');
        details = { invoice: decoded.invoice, invoiceExpiresAt: decoded.expiresAt, memo: decoded.memo };
        fee = BigInt(await session.read('lightningFee', { encodedInvoice: decoded.invoice, ...(decoded.amountAtomic === null ? { amountSats: Number(amount) } : {}) }));
      } else if (kind === 'withdraw') {
        const { validateBitcoinAddress } = await import('@midori/wallet-core');
        if (!validateBitcoinAddress(to)) throw fail('INVALID_RECIPIENT');
        const quote = await session.read('withdrawFee', { withdrawalAddress: to, amountSats: Number(amount) });
        const broadcast = quote?.l1BroadcastFeeMedium?.originalValue, service = quote?.userFeeMedium?.originalValue;
        if (![broadcast, service].every(value => Number.isSafeInteger(value) && value >= 0)) throw fail('QUOTE_UNAVAILABLE');
        fee = BigInt(broadcast) + BigInt(service);
      } else throw fail('INVALID_REQUEST');
      if (fee < 0n || await this.read('balance') < amount + fee) throw fail('INSUFFICIENT_FUNDS');
    }
    if (exchangeOrderId) {
      const order = await this.options.exchangeOrder(exchangeOrderId);
      if (kind !== 'withdraw' || order.status !== 'waiting' || order.quote.from.walletNetwork !== 'spark' || order.depositAddress !== to || parseTokenAmount(order.quote.amountFrom, 8) !== amount || order.depositReference) throw fail('PAYMENT_CHANGED');
    }
    return { id: crypto.randomUUID(), kind, from: session.descriptor.address, to, amountAtomic: String(amount), maxFeeAtomic: String(fee), expiresAt: new Date(Date.now() + 60000).toISOString(), ...details, ...(exchangeOrderId ? { exchangeOrderId } : {}) };
  }
  async authorize(quote, pinHash, stillApproved) {
    if (this.#busy) throw fail('PAYMENT_ALREADY_PENDING');
    const epoch = this.#epoch; let session, seed;
    this.#busy = true;
    const check = () => { if (epoch !== this.#epoch || !this.options.unlocked() || !stillApproved() || Date.parse(quote.expiresAt) <= Date.now() || quote.invoiceExpiresAt && Date.parse(quote.invoiceExpiresAt) <= Date.now()) throw fail('PAYMENT_CHANGED'); };
    try {
      check(); if (await this.pending()) throw fail('PAYMENT_ALREADY_PENDING');
      seed = await this.options.seed(pinHash); check();
      session = await createSparkSession(seed, { withdrawMaxFee: quote.maxFeeAtomic }); check();
      if (session.descriptor.address !== quote.from) throw fail('ACCOUNT_RECOVERY_FAILED');
      satoshis(quote.amountAtomic);
      if (quote.kind === 'lightning') { const { lightningDetails } = await sparkTools(); lightningDetails(quote.invoice); }
      if (quote.exchangeOrderId) await this.options.request('POST', `/api/v1/exchange/orders/${quote.exchangeOrderId}/spark-deposit`, { operationId: quote.id });
      check(); const key = await this.key();
      const pending = { id: quote.id, kind: quote.kind, amountAtomic: quote.amountAtomic, to: quote.to, createdAt: new Date().toISOString(), status: 'ambiguous' };
      this.options.writeLocal(key, pending);
      try {
        const result = await session.execute(quote.kind, quote);
      const hash = result?.hash || result?.transferId || result?.id;
        if (typeof hash !== 'string') return pending;
        const submitted = { ...pending, hash, status: 'submitted' };
        if (epoch === this.#epoch) this.options.writeLocal(key, submitted);
        return submitted;
      } catch { return pending; }
    } finally { seed?.fill(0); await session?.dispose(); this.#busy = false; }
  }
  lock() {
    this.#epoch++; const active = this.#active; this.#active = null; this.#registration = null;
    active?.then(session => session.dispose()).catch(() => {});
  }
}
