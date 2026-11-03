import encryption from '../encryption.js';
import { hex } from '@scure/base';

const STRING = Symbol('STRING');
const OBJECT = Symbol('OBJECT');
const BUFFER = Symbol('BUFFER'); // Uint8Array
const SETUP_BACKUP = '_midori_setup_backup';
const isAccountKey = key => key.startsWith('_cs_') || key.startsWith('_midori_');

export default class ClientStorage {
  #localStorage;
  #unlockedDetailsKey;

  constructor({ localStorage }) {
    this.#localStorage = localStorage;
    this.rollbackSetup();
  }

  #getItem(key, options) {
    const { token, type } = { type: STRING, ...options };
    let item = this.#localStorage.getItem(key);
    if (token) {
      if (!(token instanceof Uint8Array)) {
        throw new TypeError('token must be Uint8Array or Buffer');
      }
      item = encryption.decrypt(item, hex.encode(token));
    }
    if (type === STRING) {
      return item;
    } else if (type === BUFFER) {
      return hex.decode(item);
    } if (type === OBJECT) {
      return JSON.parse(item);
    }
  }
  #hasItem(key) {
    return !!this.#localStorage.getItem(key);
  }
  #setItem(key, value, options) {
    const { token, type } = { type: STRING, ...options };
    let item;
    if (type === STRING) {
      item = value;
    } else if (type === BUFFER) {
      if (!(value instanceof Uint8Array)) {
        throw new TypeError('value must be Uint8Array or Buffer');
      }
      item = hex.encode(value);
    } if (type === OBJECT) {
      item = JSON.stringify(value);
    }
    if (token) {
      if (!(token instanceof Uint8Array)) {
        throw new TypeError('token must be Uint8Array or Buffer');
      }
      item = encryption.encrypt(item, hex.encode(token));
    }
    this.#localStorage.setItem(key, item);
  }
  #unsetItem(key) {
    this.#localStorage.removeItem(key);
  }

  hasVault() {
    try {
      const record = JSON.parse(this.#localStorage.getItem('_midori_vault'));
      return record?.schema === 'midori.browser-vault' && [1, 2].includes(record.version);
    } catch { return false; }
  }

  getPinLength() {
    const length = Number(this.#localStorage.getItem('_midori_pin_length'));
    return [4, 6].includes(length) ? length : undefined;
  }
  setPinLength(length) {
    if (![4, 6].includes(length)) throw new TypeError('Invalid PIN length');
    this.#localStorage.setItem('_midori_pin_length', String(length));
  }

  beginSetup() {
    if (this.#localStorage.getItem(SETUP_BACKUP)) throw new Error('Account setup is already pending');
    const entries = Object.keys(this.#localStorage).filter(key => isAccountKey(key) && key !== SETUP_BACKUP).map(key => [key, this.#localStorage.getItem(key)]);
    this.#localStorage.setItem(SETUP_BACKUP, JSON.stringify(entries));
    this.clear({ preserveSetup: true });
  }
  commitSetup() { this.#localStorage.removeItem(SETUP_BACKUP); }
  rollbackSetup() {
    const backup = this.#localStorage.getItem(SETUP_BACKUP);
    if (!backup) return;
    const entries = JSON.parse(backup);
    if (!Array.isArray(entries) || !entries.every(entry => Array.isArray(entry) && entry.length === 2 && typeof entry[0] === 'string' && isAccountKey(entry[0]) && entry[0] !== SETUP_BACKUP && typeof entry[1] === 'string')) throw new Error('Invalid account setup backup');
    this.clear({ preserveSetup: true });
    for (const [key, value] of entries) this.#localStorage.setItem(key, value);
    this.commitSetup();
  }

  /**
   * Device ID
   */
  getId() {
    return this.#getItem('_cs_id');
  }
  hasId() {
    return this.#hasItem('_cs_id');
  }
  setId(id) {
    this.#setItem('_cs_id', id);
  }

  /**
   * Cardholder ID
   */
  getCardholderId() {
    return this.#getItem('_cs_cardholder_id');
  }
  hasCardholderId() {
    return this.#hasItem('_cs_cardholder_id');
  }
  setCardholderId(id) {
    this.#setItem('_cs_cardholder_id', id);
  }

  /**
   * PIN key
   */
  getPinKey() {
    return this.#getItem('_cs_pin_key', { type: BUFFER });
  }
  hasPinKey() {
    return /^[a-f0-9]{64}$/i.test(this.#getItem('_cs_pin_key') || '');
  }
  setPinKey(pinKey) {
    this.#setItem('_cs_pin_key', pinKey, { type: BUFFER });
  }

  /**
   * Details key
   */
  getDetailsKey() {
    if (this.#localStorage.getItem('_midori_vault')) {
      if (!this.#unlockedDetailsKey) throw new Error('Wallet is locked');
      return this.#unlockedDetailsKey;
    }
    return this.#getItem('_cs_details_key', { type: BUFFER });
  }
  setUnlockedDetailsKey(value) { this.#unlockedDetailsKey = value; }
  hasDetailsKey() {
    return this.#hasItem('_cs_details_key');
  }
  setDetailsKey(detailsKey) {
    if (this.#localStorage.getItem('_midori_vault')) {
      if (!this.#unlockedDetailsKey) this.#unlockedDetailsKey = detailsKey;
      this.#setItem('_cs_details_key', 'midori-v1');
      return;
    }
    this.#setItem('_cs_details_key', detailsKey, { type: BUFFER });
  }

  /**
   * Seeds
   */
  getSeed(type, token) {
    return this.#getItem(`_cs_seed_${type}`, { type: BUFFER, token });
  }
  hasSeed(type) {
    return this.#hasItem(`_cs_seed_${type}`);
  }
  setSeed(type, seed, token) {
    if (this.#localStorage.getItem('_midori_vault')) { this.#setItem(`_cs_seed_${type}`, 'midori-v1'); return; }
    this.#setItem(`_cs_seed_${type}`, seed, { type: BUFFER, token });
  }

  /**
   * Cache
   */
  getCache(crypto, token) {
    return this.#getItem(`_cs_cache_${crypto._id}`, { type: OBJECT, token });
  }
  hasCache(crypto) {
    return this.#hasItem(`_cs_cache_${crypto._id}`);
  }
  setCache(crypto, cache, token) {
    this.#setItem(`_cs_cache_${crypto._id}`, cache, { type: OBJECT, token });
  }
  unsetCache(crypto) {
    this.#unsetItem(`_cs_cache_${crypto._id}`);
  }

  /**
   * Public key
   */
  getPublicKey(platform, token) {
    return this.#getItem(`_cs_public_key_${platform}`, { type: OBJECT, token });
  }
  hasPublicKey(platform) {
    return this.#hasItem(`_cs_public_key_${platform}`);
  }
  setPublicKey(platform, publicKey, token) {
    this.#setItem(`_cs_public_key_${platform}`, publicKey, { type: OBJECT, token });
  }
  unsetPublicKey(platform) {
    this.#unsetItem(`_cs_public_key_${platform}`);
  }

  /**
   * Biometry
   */
  isBiometryEnabled() {
    return !!this.#getItem('_cs_biometry_enabled');
  }
  setBiometryEnabled(value) {
    if (!value) return this.#unsetItem('_cs_biometry_enabled');
    this.#setItem('_cs_biometry_enabled', value);
  }

  /**
   * Update
   */

  getUpdateShown() {
    return this.#getItem('_cs_update_shown');
  }

  setUpdateShown(value) {
    this.#setItem('_cs_update_shown', value);
  }

  /**
   * Protocol handler flag
   */

  hasProtocolHandler(scheme) {
    return this.#hasItem(`_cs_protocol_handler_${scheme}`);
  }

  setProtocolHandler(scheme) {
    this.#setItem(`_cs_protocol_handler_${scheme}`, 'registered');
  }

  /**
   * Hide/show balance
   */

  isHiddenBalance() {
    return !!this.#getItem('_cs_hidden_balance', { type: OBJECT });
  }

  toggleHiddenBalance() {
    this.#setItem('_cs_hidden_balance', !this.isHiddenBalance(), { type: OBJECT });
  }

  /**
   * Tor
   */

  isOnion() {
    return !!this.#getItem('_cs_onion', { type: OBJECT });
  }

  toggleOnion() {
    this.#setItem('_cs_onion', !this.isOnion(), { type: OBJECT });
  }

  /**
   * Theme
   */

  getTheme() {
    return this.#getItem('_cs_theme');
  }

  setTheme(theme) {
    this.#setItem('_cs_theme', theme);
  }


  /**
   * Clean All!
   */

  clear({ preserveSetup = false } = {}) {
    this.#unlockedDetailsKey = null;
    for (const key of Object.keys(this.#localStorage)) {
      if (preserveSetup && key === SETUP_BACKUP) continue;
      if (key.startsWith('_midori_')) { this.#localStorage.removeItem(key); continue; }
      if (key && key.startsWith('_cs_') && !['_cs_language', '_cs_theme'].includes(key)) {
        this.#localStorage.removeItem(key);
      }
    }
  }
}
