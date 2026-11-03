import { readFile, writeFile } from 'node:fs/promises';
import { createPrivateKey } from 'node:crypto';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { MidoriClient } from '../lib/client.js';

export async function inspectAccount({ directory, origin = 'http://127.0.0.1:8080' }) {
  const location = resolve(directory);
  const backup = JSON.parse(await readFile(join(location, 'backup.encrypted.json'), 'utf8'));
  if (backup.schema !== 'midori.client-backup' || backup.version !== 1 || backup.kdf.name !== 'PBKDF2-SHA256' || backup.kdf.iterations !== 600000 || backup.cipher.name !== 'AES-256-GCM') throw new Error('Unsupported local backup');
  const password = (await readFile(join(location, 'recovery-password.txt'), 'utf8')).trim();
  const material = await crypto.subtle.importKey('raw', Buffer.from(password), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', iterations: 600000, salt: Buffer.from(backup.kdf.salt, 'base64url') }, material, { name: 'AES-GCM', length: 256 }, false, ['decrypt']);
  const bytes = Buffer.from(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: Buffer.from(backup.cipher.iv, 'base64url'), additionalData: Buffer.from('midori.client-backup/v1') }, key, Buffer.from(backup.cipher.ciphertext, 'base64url')));
  let payload;
  try { payload = JSON.parse(bytes.toString()); } finally { bytes.fill(0); }
  const session = JSON.parse(await readFile(join(location, 'session.json'), 'utf8'));
  const client = new MidoriClient({ origin, session, privateKey: createPrivateKey(payload.devicePrivateKey) });
  const me = await client.request('GET', '/api/v1/me');
  const account = me.accounts.find(account => account.address === payload.descriptor.address);
  if (!account) throw new Error('Restored descriptor does not match registered account');
  const balances = await client.request('GET', `/api/v1/accounts/${account.id}/balances`);
  const result = { checkedAt: new Date().toISOString(), walletId: me.walletId, accountId: account.id, chainId: 8453, network: 'base', address: account.address, balances, localEncryptedBackupOpened: true, transactionsSent: 0 };
  await writeFile(join(location, 'mainnet-inspection.json'), JSON.stringify(result, null, 2) + '\n', { mode: 0o600 });
  return result;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(JSON.stringify(await inspectAccount({ directory: process.argv[2], origin: process.env.MIDORI_API_ORIGIN || 'http://127.0.0.1:8080' }), null, 2)); }
  catch (error) { console.error(JSON.stringify({ event: 'account_inspection_failed', code: error.code || 'CLIENT_ERROR' })); process.exitCode = 1; }
}
