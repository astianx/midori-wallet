import { ed25519 } from '@noble/curves/ed25519';
import { hmac } from '@noble/hashes/hmac';
import { sha256 } from '@noble/hashes/sha256';
import { hex } from '@scure/base';
import { AccountAdapter } from './AccountAdapter.js';
import { BASE_ASSETS, basePlatform, validateAssets } from './AssetCatalog.js';
import { isUnavailable } from './domain.js';
import { MidoriClient, EncryptedBlobSync } from '../api/MidoriClient.js';
import { LocalVault, encode, decode } from './LocalVault.js';
import { decryptLegacyStorage, decrypt, encrypt } from '../encryption.js';

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const random = () => crypto.getRandomValues(new Uint8Array(32));
const platform = basePlatform;

export default class MidoriSession {
  #payload; #deviceSeed; #walletSeed; #detailsKey;
  #generation = 0;
  #adapter = new AccountAdapter();
  constructor({ localStorage, clientStorage }) {
    this.storage = localStorage;
    this.clientStorage = clientStorage;
    this.vault = new LocalVault(localStorage);
    this.origin = import.meta.env.VITE_API_ORIGIN || window.location.origin;
    this.publicClient = new MidoriClient({ origin: this.origin });
    this.client = this.publicClient;
  }

  async initialize(payload) {
    this.#payload = payload;
    this.tag = undefined;
    this.#deviceSeed = decode(payload.deviceSeed);
    this.#walletSeed = decode(payload.walletSeed);
    this.#detailsKey = decode(payload.detailsKey);
    this.clientStorage.setUnlockedDetailsKey(this.#detailsKey);
    this.assets = validateAssets(BASE_ASSETS);
    this.descriptor = payload.descriptor;
    this.account = { id: null, address: payload.descriptor.address, descriptor: payload.descriptor };
    this.config = { network: { chainId: 8453, testnet: false }, capabilities: {} };
    this.connection = 'offline';
    try {
      const config = await this.publicClient.request('GET', '/api/v1/config');
      if (config.network.chainId !== 8453 || config.network.testnet) throw new Error('Expected Base mainnet');
      const registry = await this.publicClient.request('GET', '/api/v1/assets');
      this.assets = validateAssets(registry.assets);
      this.config = config;
      this.connection = 'online';
    } catch (error) { if (!isUnavailable(error)) throw error; }
  }

  async create(walletSeed, deviceSeed, pinHash, detailsKey, descriptor) {
    const recovered = descriptor ? await this.#adapter.restore(walletSeed, descriptor) : await this.#adapter.create(walletSeed);
    const wallet = { descriptor: recovered, signMessage: message => this.#adapter.signMessage(message), dispose: () => this.#adapter.lock() };
    try {
      const payload = { version: 1, descriptor: wallet.descriptor, walletSeed: encode(walletSeed), deviceSeed: encode(deviceSeed), detailsKey: encode(detailsKey), deviceToken: encode(random()), walletToken: encode(random()) };
      await this.vault.save(payload, pinHash);
      await this.initialize(payload);
      if (this.connection === 'online') {
        try { await this.enroll(); }
        catch (error) { if (!isUnavailable(error)) throw error; this.connection = 'offline'; }
      }
      return { deviceToken: hex.encode(decode(payload.deviceToken)), walletToken: hex.encode(decode(payload.walletToken)), deviceId: this.session?.deviceId || 'local' };
    } finally { wallet.dispose(); }
  }

  async enroll() {
    const root = hmac(sha256, 'midori.api.identity.v1', this.#walletSeed);
    try {
      const walletPublicKey = encode(ed25519.getPublicKey(root));
      const devicePublicKey = encode(ed25519.getPublicKey(this.#deviceSeed));
      const options = await this.publicClient.request('POST', '/api/v1/registration/options', { walletPublicKey, devicePublicKey, descriptor: this.descriptor });
      let session;
      try {
        await this.#adapter.restore(this.#walletSeed, this.descriptor);
        session = await this.publicClient.request('POST', '/api/v1/registration/verify', { id: options.id, challenge: options.challenge, walletSignature: encode(ed25519.sign(encoder.encode(options.message), root)), deviceSignature: encode(ed25519.sign(encoder.encode(options.message), this.#deviceSeed)), ownerSignature: await this.#adapter.signMessage(options.message) });
      } catch (error) {
        if (error.code !== 'DUPLICATE_RESOURCE') throw error;
        const recovery = await this.publicClient.request('POST', '/api/v1/recovery/options', { walletPublicKey, devicePublicKey });
        session = await this.publicClient.request('POST', '/api/v1/recovery/verify', { id: recovery.id, challenge: recovery.challenge, walletSignature: encode(ed25519.sign(encoder.encode(recovery.message), root)), deviceSignature: encode(ed25519.sign(encoder.encode(recovery.message), this.#deviceSeed)) });
      }
      this.storage.setItem('_midori_identity', JSON.stringify({ walletId: session.walletId, deviceId: session.deviceId }));
      this.clientStorage.setId(session.deviceId);
      await this.setSession(session);
    } finally { root.fill(0); this.#adapter.lock(); }
  }

  async setSession(session) {
    this.session = session;
    this.client = new MidoriClient({ origin: this.origin, session, signMessage: async message => ed25519.sign(message, this.#deviceSeed) });
    this.blobs = new EncryptedBlobSync(this.client);
    const me = await this.client.request('GET', '/api/v1/me');
    this.tag = this.config.capabilities.tags ? (await this.client.request('GET', '/api/v1/tag')).tag : null;
    this.account = me.accounts.find(account => account.address === this.#payload.descriptor.address);
    if (!this.account || JSON.stringify(this.account.descriptor) !== JSON.stringify(this.#payload.descriptor)) {
      // Compare fields rather than JSON key order from PostgreSQL JSONB.
      const equal = (left, right) => left && right && Object.keys(left).length === Object.keys(right).length && Object.keys(left).every(key => typeof left[key] === 'object' ? equal(left[key], right[key]) : left[key] === right[key]);
      if (!this.account || !equal(this.account.descriptor, this.#payload.descriptor)) throw new Error('Recovered account does not match the local descriptor');
    }
    this.connection = 'online';
  }

  async unlock(pinHash) {
    const payload = await this.vault.open(pinHash);
    await this.#adapter.restore(decode(payload.walletSeed), payload.descriptor);
    this.#adapter.lock();
    await this.initialize(payload);
    return { deviceToken: hex.encode(decode(payload.deviceToken)), walletToken: hex.encode(decode(payload.walletToken)) };
  }

  async open(deviceSeed) {
    if (!this.#deviceSeed || hex.encode(deviceSeed) !== hex.encode(this.#deviceSeed)) throw Object.assign(new Error('Wallet is locked'), { status: 401 });
    if (this.connection !== 'online') return;
    try {
      const identity = JSON.parse(this.storage.getItem('_midori_identity'));
      if (!identity) { await this.enroll(); return; }
      const options = await this.publicClient.request('POST', '/api/v1/auth/options', { deviceId: identity.deviceId });
      const session = await this.publicClient.request('POST', '/api/v1/auth/verify', { id: options.id, challenge: options.challenge, signature: encode(ed25519.sign(encoder.encode(options.message), this.#deviceSeed)) });
      await this.setSession(session);
    } catch (error) { if (!isUnavailable(error)) throw error; this.connection = 'offline'; }
  }

  async exportPrivateKeys(pinHash) {
    if (!this.#payload) throw Object.assign(new Error('Wallet is locked'), { status: 401 });
    const generation = this.#generation;
    const descriptor = this.descriptor;
    const payload = await this.vault.open(pinHash);
    const seed = decode(payload.walletSeed);
    try {
      if (generation !== this.#generation || payload.descriptor.address !== descriptor.address || payload.descriptor.ownerAddress !== descriptor.ownerAddress) throw new Error('Wallet is locked');
      const data = await this.#adapter.exportPrivateKeys(seed, payload.descriptor);
      if (generation !== this.#generation) throw new Error('Wallet is locked');
      return [data];
    } finally { seed.fill(0); }
  }

  getSeed(type, token) {
    if (!this.#payload || !(token instanceof Uint8Array)) throw Object.assign(new Error('Wallet is locked'), { status: 401 });
    if (type === 'cardholder' && hex.encode(token) === hex.encode(this.#deviceSeed)) return hmac(sha256, 'Cards', hex.encode(this.#walletSeed));
    const expected = type === 'device' ? this.#payload.deviceToken : type === 'wallet' ? this.#payload.walletToken : null;
    if (!expected || encode(token) !== expected) throw Object.assign(new Error('Invalid unlock token'), { status: 401 });
    return new Uint8Array(type === 'device' ? this.#deviceSeed : this.#walletSeed);
  }

  namespace(name) { return name === 'details' ? 'metadata' : `s_${hex.encode(sha256(name)).slice(0, 56)}`; }

  async cipherKey() { return crypto.subtle.importKey('raw', this.#detailsKey, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']); }

  async readData(name, migrationAttempt = 0) {
    const namespace = this.namespace(name);
    let blob;
    try {
      if (!this.blobs || this.connection !== 'online') throw Object.assign(new Error(), { code: 'NETWORK_UNCONFIRMED' });
      blob = await this.blobs.read(namespace);
      this.storage.setItem(`_midori_blob_${namespace}`, JSON.stringify(blob));
    } catch (error) {
      if (!isUnavailable(error)) throw error;
      blob = JSON.parse(this.storage.getItem(`_midori_blob_${namespace}`) || 'null');
    }
    if (!blob || blob.deleted) return null;
    const envelope = JSON.parse(decoder.decode(decode(blob.ciphertext)));
    if (envelope.version !== 1) throw new Error('Unsupported encrypted metadata');
    const bytes = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: decode(envelope.iv), additionalData: encoder.encode(namespace) }, await this.cipherKey(), decode(envelope.ciphertext)));
    try {
      const data = JSON.parse(decoder.decode(bytes));
      if (typeof data !== 'string' || !data.startsWith('U2FsdGVkX1')) return data;
      const key = hex.encode(this.#detailsKey);
      const cleartext = await decryptLegacyStorage(data, key);
      const value = JSON.parse(cleartext);
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid legacy storage document');
      const migrated = encrypt(cleartext, key);
      if (decrypt(migrated, key) !== cleartext) throw new Error('Storage migration verification failed');
      const backupKey = `_midori_migration_backup_${namespace}`;
      if (!this.storage.getItem(backupKey)) this.storage.setItem(backupKey, JSON.stringify(blob));
      try { await this.writeData(name, migrated); }
      catch (error) {
        if (error.code === 'REVISION_CONFLICT' && migrationAttempt === 0) return this.readData(name, 1);
        if (!isUnavailable(error) && error.code !== 'REVISION_CONFLICT') throw error;
      }
      return migrated;
    } finally { bytes.fill(0); }
  }

  async writeData(name, data) {
    const namespace = this.namespace(name);
    if (this.connection !== 'online' || !this.blobs) throw Object.assign(new Error('Midori is offline; settings were not saved.'), { code: 'NETWORK_UNCONFIRMED' });
    if (this.blobs.revision(namespace) === undefined) await this.blobs.read(namespace);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const clear = encoder.encode(JSON.stringify(data));
    let ciphertext;
    try { ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: encoder.encode(namespace) }, await this.cipherKey(), clear)); }
    finally { clear.fill(0); }
    const envelope = { version: 1, iv: encode(iv), ciphertext: encode(ciphertext) };
    const blob = await this.blobs.write(namespace, { ciphertext: encode(encoder.encode(JSON.stringify(envelope))) });
    this.storage.setItem(`_midori_blob_${namespace}`, JSON.stringify(blob));
    return data;
  }

  async balances() {
    if (!this.#payload) throw new Error('Account is locked');
    if (!this.balanceRequest) {
      const generation = this.#generation;
      this.balanceRequest = (async () => {
        let result;
        try {
          if (this.connection !== 'online' || !this.account?.id) throw Object.assign(new Error(), { code: 'NETWORK_UNCONFIRMED' });
          result = await this.client.request('GET', `/api/v1/accounts/${this.account.id}/balances`);
        } catch (error) {
          if (!isUnavailable(error)) throw error;
          const assets = await this.#adapter.balances(this.descriptor, this.assets, import.meta.env.VITE_BASE_RPC_URL);
          result = { chainId: 8453, address: this.descriptor.address, readAt: new Date().toISOString(), assets };
        }
        if (generation !== this.#generation) throw new Error('Account is locked');
        if (result.chainId !== 8453 || result.address.toLowerCase() !== this.descriptor.address.toLowerCase()) throw new Error('Unexpected balance account');
        return result;
      })().finally(() => { if (generation === this.#generation) this.balanceRequest = null; });
    }
    return this.balanceRequest;
  }

  async resolveRecipient(value) {
    const text = value.trim();
    if (text.startsWith('@')) {
      const result = await this.client.request('POST', '/api/v1/tags/resolve', { alias: text, chainId: 8453 });
      if (result.chainId !== 8453 || Date.parse(result.expiresAt) <= Date.now()) throw Object.assign(new Error(), { code: 'TAG_RECONFIRM_REQUIRED' });
      return { kind: 'midori_tag', address: result.address, alias: `@${result.alias}`, resolutionId: result.id, revision: result.revision, expiresAt: result.expiresAt };
    }
    if (!/^0x[0-9a-fA-F]{40}$/.test(text) || /^0x0{40}$/i.test(text) || text.toLowerCase() === this.descriptor.address.toLowerCase()) throw Object.assign(new Error(), { code: 'INVALID_RECIPIENT' });
    return { kind: 'address', address: text };
  }

  async reviewPayment(request) {
    if (!this.session || !this.account?.id) throw Object.assign(new Error(), { code: 'NETWORK_UNCONFIRMED' });
    return this.client.request('POST', `/api/v1/accounts/${this.account.id}/intents`, request);
  }
  async payments() {
    if (!this.session || !this.account?.id) return { intents: [] };
    return this.client.request('GET', `/api/v1/accounts/${this.account.id}/intents`);
  }
  async payment(id) { return this.client.request('GET', `/api/v1/intents/${id}`); }
  async cancelPayment(id) { return this.client.request('POST', `/api/v1/intents/${id}/cancel`, {}); }

  async authorizePayment(intent, pinHash, expectedContract, stillApproved = () => true) {
    if (!this.#payload || !intent?.quote || intent.status !== 'quoted') throw Object.assign(new Error(), { code: 'QUOTE_EXPIRED' });
    const generation = this.#generation;
    const quote = intent.quote;
    if (!expectedContract || expectedContract.toLowerCase() !== intent.contract.toLowerCase() || !this.assets.some(asset => asset.contract.toLowerCase() === intent.contract.toLowerCase()) || Date.parse(quote.expiresAt) > Date.now() + 300000) throw Object.assign(new Error(), { code: 'PAYMENT_CHANGED' });
    if (intent.from.toLowerCase() !== this.descriptor.address.toLowerCase() || quote.contract.toLowerCase() !== intent.contract.toLowerCase() || quote.recipient.toLowerCase() !== intent.to.toLowerCase() || quote.amountAtomic !== intent.amountAtomic || quote.feeMode !== intent.feeMode || quote.expiresAt !== intent.expiresAt || Date.parse(quote.expiresAt) <= Date.now()) throw Object.assign(new Error(), { code: 'PAYMENT_CHANGED' });
    // Network cost is always paid in the same token being sent (self-pay):
    // the quoted feeToken must match the intent's own contract, not a fixed
    // gas token. `intent.contract` was already confirmed above to be one of
    // this account's known assets.
    if (quote.feeMode === 'token' && (quote.feeToken.toLowerCase() !== intent.contract.toLowerCase() || BigInt(quote.maxFeeAtomic) > BigInt(this.config.gas.maxFeeAtomic))) throw Object.assign(new Error(), { code: 'PAYMENT_CHANGED' });
    const payload = await this.vault.open(pinHash);
    const seed = decode(payload.walletSeed);
    try {
      if (generation !== this.#generation || !stillApproved() || payload.descriptor.address !== this.descriptor.address) throw Object.assign(new Error(), { code: 'PAYMENT_CHANGED' });
      await this.#adapter.restore(seed, payload.descriptor);
      const signature = await this.#adapter.signPayment(quote.operation, quote.expiresAt, quote);
      if (generation !== this.#generation || !stillApproved()) throw Object.assign(new Error(), { code: 'PAYMENT_CHANGED' });
      this.storage.setItem(`_midori_pending_${this.descriptor.address}`, intent.id);
      try { return await this.client.request('POST', `/api/v1/intents/${intent.id}/authorize`, { quoteDigest: intent.quoteDigest, signature }); }
      catch (error) {
        if (!isUnavailable(error)) throw error;
        try { return await this.payment(intent.id); }
        catch { return { ...intent, status: 'ambiguous', failureCode: 'NETWORK_UNCONFIRMED' }; }
      }
    } finally { seed.fill(0); this.#adapter.lock(); }
  }

  async updateTag(value) {
    if (!this.session || !this.account?.id || this.connection !== 'online') throw Object.assign(new Error('Midori is offline; the tag was not saved.'), { code: 'NETWORK_UNCONFIRMED' });
    const generation = this.#generation;
    const alias = value.trim().replace(/^@/, '').toLowerCase();
    if (this.tag?.alias === alias) return alias;
    const options = await this.client.request('POST', '/api/v1/tag/options', { alias, accountId: this.account.id });
    if (generation !== this.#generation || options.alias !== alias || options.chainId !== 8453 || options.address !== this.descriptor.address) throw new Error('Tag confirmation changed');
    const fields = options.message.split('\n');
    if (fields.length !== 10 || fields[0] !== 'Midori tag v1' || fields[1] !== this.config.authentication.origin || fields[2] !== options.id || fields[4] !== this.session.walletId || fields[5] !== this.account.id || fields[6] !== '8453' || fields[7] !== this.descriptor.address || fields[8] !== `@${alias}` || fields[9] !== options.expiresAt || Date.parse(options.expiresAt) <= Date.now()) throw new Error('Invalid tag confirmation');
    try {
      await this.#adapter.restore(this.#walletSeed, this.descriptor);
      const signature = await this.#adapter.signMessage(options.message);
      if (generation !== this.#generation) throw new Error('Account is locked');
      const result = await this.client.request('POST', '/api/v1/tag/verify', { id: options.id, signature });
      if (generation !== this.#generation) throw new Error('Account is locked');
      this.tag = result.tag;
      return this.tag.alias;
    } finally { this.#adapter.lock(); }
  }

  async history(contract, cursor) {
    if (!this.session || !this.account?.id) throw Object.assign(new Error('Connect to Midori to update history.'), { code: 'NETWORK_UNCONFIRMED' });
    const query = new URLSearchParams({ contract });
    if (cursor) query.set('cursor', cursor);
    return this.client.request('GET', `/api/v1/accounts/${this.account.id}/history?${query}`);
  }

  async request(config) {
    const target = new URL(config.url, this.origin);
    if (target.origin !== new URL(this.origin).origin || config.baseURL && new URL(config.baseURL, this.origin).origin !== new URL(this.origin).origin) throw Object.assign(new Error('External API is not enabled'), { code: 'CAPABILITY_UNAVAILABLE' });
    const path = target.pathname;
    const method = (config.method || 'get').toLowerCase();
    if (path === '/local/token/device/pin' || path === '/local/token/wallet/pin') return this.unlock(config.data.pinHash);
    if (path === '/local/token/wallet' && this.#payload) return { walletToken: hex.encode(decode(this.#payload.walletToken)) };
    if (path === '/api/v1/cryptos') return [...this.assets, platform].map(asset => ({ ...asset, meta: { ...asset.meta } }));
    if (path === '/local/settings') {
      const settings = await this.readData('settings') || { '1faWallet': true };
      if (method !== 'get') Object.assign(settings, config.data);
      if (method !== 'get') await this.writeData('settings', settings);
      const list = this.connection === 'online' ? await this.client.request('GET', '/api/v1/authenticators') : { authenticators: [] };
      return { ...settings, hasAuthenticators: list.authenticators.some(item => !item.revokedAt) };
    }
    if (path === '/local/details') return { data: method === 'get' ? await this.readData('details') : await this.writeData('details', config.data.data) };
    if (path.startsWith('/local/storage/')) {
      const name = decodeURIComponent(path.slice('/local/storage/'.length));
      return { data: method === 'get' ? await this.readData(name) : await this.writeData(name, config.data.data) };
    }
    if (path.startsWith('/local/storages/')) return Promise.all(decodeURIComponent(path.slice('/local/storages/'.length)).split(',').map(async name => ({ _id: name, data: await this.readData(name) })));
    if (path.startsWith('/api/v1/')) return this.client.request(method.toUpperCase(), path + target.search, config.data);
    throw Object.assign(new Error('This option is not connected to Midori yet'), { status: 503, code: 'CAPABILITY_UNAVAILABLE' });
  }

  lock() {
    this.#deviceSeed?.fill(0); this.#walletSeed?.fill(0); this.#detailsKey?.fill(0);
    this.#generation++; this.#adapter.lock();
    this.#payload = null; this.session = null; this.blobs = null; this.balanceRequest = null;
    this.#deviceSeed = null; this.#walletSeed = null; this.#detailsKey = null;
    this.client = this.publicClient; this.account = null; this.descriptor = null; this.tag = null;
    this.clientStorage.setUnlockedDetailsKey(null);
  }
}

export { platform };
