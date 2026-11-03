const ITERATIONS = 600000;
const encoder = new TextEncoder();
const hex = bytes => [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
function bytes(value, length) {
  if (typeof value !== 'string' || !/^(?:[0-9a-f]{2})+$/.test(value) || (length && value.length !== length * 2)) throw new Error('Invalid vault encoding');
  return Uint8Array.from(value.match(/../g), pair => parseInt(pair, 16));
}
async function derive(password, salt) {
  if (typeof password !== 'string' || password.length < 12) throw new Error('Use a vault password of at least 12 characters');
  const material = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: ITERATIONS }, material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}
export async function sealVault(seed, descriptor, password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await derive(password, salt);
  const cleartext = encoder.encode(seed);
  try {
    const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: encoder.encode(JSON.stringify(descriptor)), tagLength: 128 }, key, cleartext);
    return { schema: 'midori.f1-vault', version: 1, descriptor, kdf: { name: 'PBKDF2', hash: 'SHA-256', iterations: ITERATIONS, salt: hex(salt) }, cipher: { name: 'AES-256-GCM', iv: hex(iv), ciphertext: hex(new Uint8Array(ciphertext)) } };
  } finally { cleartext.fill(0); }
}
export async function openVault(vault, password) {
  if (vault?.schema !== 'midori.f1-vault' || vault.version !== 1 || vault.kdf?.name !== 'PBKDF2' || vault.kdf.hash !== 'SHA-256' || vault.kdf.iterations !== ITERATIONS || vault.cipher?.name !== 'AES-256-GCM' || vault.cipher.ciphertext?.length > 16384) throw new Error('Unsupported vault parameters');
  const key = await derive(password, bytes(vault.kdf.salt, 16));
  const cleartext = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: bytes(vault.cipher.iv, 12), additionalData: encoder.encode(JSON.stringify(vault.descriptor)), tagLength: 128 }, key, bytes(vault.cipher.ciphertext)));
  try { return new TextDecoder().decode(cleartext); } finally { cleartext.fill(0); }
}
