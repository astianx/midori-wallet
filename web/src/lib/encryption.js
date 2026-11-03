import { base64, hex } from '@scure/base';
import { cbc, gcm } from '@noble/ciphers/aes.js';
import { hkdf } from '@noble/hashes/hkdf.js';
import { sha256 } from '@noble/hashes/sha256.js';
import { randomBytes, utf8ToBytes, bytesToUtf8 } from '@noble/hashes/utils.js';

const context = utf8ToBytes('midori.storage/aes-256-gcm/v1');
const limit = 2 * 1024 * 1024;

function keyMaterial(token) {
  if (typeof token !== 'string' || !/^(?:[a-f0-9]{64}|[a-f0-9]{128})$/i.test(token)) throw new TypeError('A random storage key of at least 256 bits is required');
  return hex.decode(token);
}

function derive(token, salt) {
  const material = keyMaterial(token);
  try { return hkdf(sha256, material, salt, context, 32); }
  finally { material.fill(0); }
}

export function encrypt(data, token) {
  if (typeof data !== 'string' || data.length > limit) throw new TypeError('Invalid storage data');
  const salt = randomBytes(32);
  const iv = randomBytes(12);
  const key = derive(token, salt);
  const clear = utf8ToBytes(data);
  try {
    const ciphertext = gcm(key, iv, context).encrypt(clear);
    return JSON.stringify({ schema: 'midori.storage', version: 1, salt: base64.encode(salt), iv: base64.encode(iv), ciphertext: base64.encode(ciphertext) });
  } finally { key.fill(0); clear.fill(0); }
}

export function decrypt(data, token) {
  if (typeof data !== 'string' || data.length > limit * 2) throw new TypeError('Invalid storage data');
  const record = JSON.parse(data);
  if (record?.schema !== 'midori.storage' || record.version !== 1 || Object.keys(record).sort().join(',') !== 'ciphertext,iv,salt,schema,version') throw new Error('Unsupported storage envelope');
  const salt = base64.decode(record.salt);
  const iv = base64.decode(record.iv);
  const ciphertext = base64.decode(record.ciphertext);
  if (salt.length !== 32 || iv.length !== 12 || ciphertext.length < 16) throw new Error('Invalid storage parameters');
  const key = derive(token, salt);
  let clear;
  try { clear = gcm(key, iv, context).decrypt(ciphertext); return bytesToUtf8(clear); }
  finally { key.fill(0); clear?.fill(0); }
}

// Read-only migration, after the enclosing Midori AES-GCM blob has been authenticated.
export async function decryptLegacyStorage(data, token) {
  if (typeof data !== 'string' || data.length > limit * 2 || !data.startsWith('U2FsdGVkX1')) throw new Error('Unsupported legacy storage');
  const material = keyMaterial(token);
  material.fill(0);
  const encoded = base64.decode(data);
  if (encoded.length < 32 || (encoded.length - 16) % 16 || bytesToUtf8(encoded.subarray(0, 8)) !== 'Salted__') throw new Error('Invalid legacy storage');
  const { md5 } = await import('@noble/hashes/legacy.js');
  const password = utf8ToBytes(token);
  const derived = new Uint8Array(48);
  let block = new Uint8Array(0), clear;
  try {
    for (let offset = 0; offset < derived.length; offset += 16) {
      const input = new Uint8Array(block.length + password.length + 8);
      input.set(block); input.set(password, block.length); input.set(encoded.subarray(8, 16), block.length + password.length);
      try { const next = md5(input); block.fill(0); block = next; }
      finally { input.fill(0); }
      derived.set(block, offset);
    }
    clear = cbc(derived.subarray(0, 32), derived.subarray(32, 48)).decrypt(encoded.subarray(16));
    return bytesToUtf8(clear);
  } finally { password.fill(0); derived.fill(0); block.fill(0); clear?.fill(0); }
}

export const encryptJSON = (json, key) => encrypt(JSON.stringify(json), key);
export const decryptJSON = (text, key) => JSON.parse(decrypt(text, key));
export default { encrypt, decrypt, encryptJSON, decryptJSON };
