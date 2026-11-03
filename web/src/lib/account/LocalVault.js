import { platformProtection, wrappingKey } from '../storage/PlatformProtection.js';
const encoder = new TextEncoder();
const decoder = new TextDecoder();
export const encode = bytes => {
  let text = '';
  for (let offset = 0; offset < bytes.length; offset += 8192) text += String.fromCharCode(...bytes.subarray(offset, offset + 8192));
  return btoa(text).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
};
export const decode = text => Uint8Array.from(atob(text.replaceAll('-', '+').replaceAll('_', '/')), character => character.charCodeAt(0));
const aad = encoder.encode('midori.browser-vault/v1');
async function pinKey(pinHash, salt) {
  if (typeof pinHash !== 'string' || pinHash.length < 32 || salt.length !== 16) throw new Error('Invalid vault key');
  const material = await crypto.subtle.importKey('raw', encoder.encode(pinHash), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: 600000 }, material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}
function validate(payload) {
  if (payload?.version !== 1 || payload.descriptor?.schema !== 'midori.safe-account' || payload.descriptor.chainId !== 8453 || decode(payload.walletSeed).length !== 64 || ['deviceSeed','detailsKey','deviceToken','walletToken'].some(key => decode(payload[key]).length !== 32)) throw new Error('Invalid vault payload');
  return payload;
}
export class LocalVault {
  constructor(storage, options = {}) { this.storage = storage; this.protection = options.protection; }
  protector() { return this.protection || platformProtection(); }
  async save(payload, pinHash) {
    validate(payload);
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const bytes = encoder.encode(JSON.stringify(payload));
    let cipher;
    try { cipher = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: aad }, await pinKey(pinHash, salt), bytes); }
    finally { bytes.fill(0); }
    const protector = this.protector();
    const wrapped = await protector.seal(JSON.stringify({ salt: encode(salt), iv: encode(iv), ciphertext: encode(new Uint8Array(cipher)) }));
    const record = { schema: 'midori.browser-vault', version: 2, protection: wrapped };
    const old = this.storage.getItem('_midori_vault');
    try {
      const restored = await this.openRecord(record, pinHash);
      if (JSON.stringify(restored) !== JSON.stringify(payload)) throw new Error('Local recovery verification failed');
      this.storage.setItem('_midori_vault', JSON.stringify(record));
    } catch (error) { await protector.remove?.(wrapped); throw error; }
    if (old) { const previous = JSON.parse(old); if (previous.version === 2) await protector.remove?.(previous.protection).catch(() => {}); }
    this.storage.removeItem('_midori_pin_guard');
  }
  async openRecord(record, pinHash) {
    if (record?.schema !== 'midori.browser-vault' || ![1,2].includes(record.version)) throw new Error('Unsupported vault');
    let outer, clear;
    try {
      if (record.version === 1) {
        outer = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: decode(record.iv), additionalData: aad }, await wrappingKey(), decode(record.ciphertext)));
      } else outer = encoder.encode(await this.protector().open(record.protection));
      const inner = JSON.parse(decoder.decode(outer));
      if (decode(inner.iv).length !== 12 || inner.ciphertext.length > 32768) throw new Error('Invalid vault envelope');
      clear = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: decode(inner.iv), additionalData: aad }, await pinKey(pinHash, decode(inner.salt)), decode(inner.ciphertext)));
      return validate(JSON.parse(decoder.decode(clear)));
    } finally { outer?.fill(0); clear?.fill(0); }
  }
  async open(pinHash) {
    let guard;
    try { guard = JSON.parse(this.storage.getItem('_midori_pin_guard') || '{"attempts":0,"until":0}'); } catch { guard = { attempts: 5, until: Date.now() + 30000 }; }
    if (guard.until > Date.now()) throw Object.assign(new Error('Wait before trying the PIN again'), { status: 429 });
    try {
      const record = JSON.parse(this.storage.getItem('_midori_vault'));
      const payload = await this.openRecord(record, pinHash);
      if (record.version === 1) await this.save(payload, pinHash);
      this.storage.removeItem('_midori_pin_guard');
      return payload;
    } catch {
      const attempts = Number.isSafeInteger(guard.attempts) ? guard.attempts + 1 : 5;
      this.storage.setItem('_midori_pin_guard', JSON.stringify({ attempts, until: attempts >= 5 ? Date.now() + 30000 : 0 }));
      throw Object.assign(new Error('Incorrect PIN or invalid local vault'), { status: 401 });
    }
  }
}
