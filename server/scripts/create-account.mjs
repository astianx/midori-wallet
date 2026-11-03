import { generateKeyPairSync, randomBytes, sign } from 'node:crypto';
import { mkdir, writeFile, readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { Wallet } from 'ethers';
import { MidoriClient } from '../lib/client.js';

export async function createAccount({ origin = 'http://127.0.0.1:8080', directory }) {
  if (!directory) throw new Error('A private backup directory is required');
  const location = resolve(directory);
  await mkdir(location, { recursive: true, mode: 0o700 });
  const { createWalletSession } = await import('../../packages/wallet-core/src/index.js');
  const identity = generateKeyPairSync('ed25519'); const device = generateKeyPairSync('ed25519');
  const walletPublicKey = identity.publicKey.export({ type: 'spki', format: 'der' }).subarray(-32).toString('base64url');
  const devicePublicKey = device.publicKey.export({ type: 'spki', format: 'der' }).subarray(-32).toString('base64url');
  const owner = Wallet.createRandom();
  const wallet = await createWalletSession(owner.mnemonic.phrase, { chainId: 8453 });
  const descriptor = wallet.descriptor;
  wallet.dispose();
  const recoveryPassword = randomBytes(32).toString('base64url');
  const salt = randomBytes(16); const iv = randomBytes(12);
  const material = await crypto.subtle.importKey('raw', Buffer.from(recoveryPassword), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', iterations: 600000, salt }, material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
  const payload = Buffer.from(JSON.stringify({ mnemonic: owner.mnemonic.phrase, descriptor, identityPrivateKey: identity.privateKey.export({ type: 'pkcs8', format: 'pem' }), devicePrivateKey: device.privateKey.export({ type: 'pkcs8', format: 'pem' }) }));
  let sealed;
  try {
    sealed = Buffer.from(await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: Buffer.from('midori.client-backup/v1') }, key, payload));
  } finally { payload.fill(0); }
  const encryptedBackup = { schema: 'midori.client-backup', version: 1, kdf: { name: 'PBKDF2-SHA256', iterations: 600000, salt: salt.toString('base64url') }, cipher: { name: 'AES-256-GCM', iv: iv.toString('base64url'), ciphertext: sealed.toString('base64url') } };
  const privateWrite = (name, value) => writeFile(join(location, name), value, { flag: 'wx', mode: 0o600 });
  await privateWrite('backup.encrypted.json', JSON.stringify(encryptedBackup));
  await privateWrite('recovery-password.txt', recoveryPassword + '\n');
  await privateWrite('descriptor.json', JSON.stringify(descriptor, null, 2) + '\n');
  const localCopy = JSON.parse(await readFile(join(location, 'backup.encrypted.json'), 'utf8'));
  const clear = Buffer.from(await crypto.subtle.decrypt({ name: 'AES-GCM', iv, additionalData: Buffer.from('midori.client-backup/v1') }, key, Buffer.from(localCopy.cipher.ciphertext, 'base64url')));
  try { if (JSON.parse(clear.toString()).descriptor.address !== descriptor.address) throw new Error('Local backup verification failed'); } finally { clear.fill(0); }
  const client = new MidoriClient({ origin });
  const options = await client.request('POST', '/api/v1/registration/options', { walletPublicKey, devicePublicKey, descriptor });
  const signature = privateKey => sign(null, Buffer.from(options.message), privateKey).toString('base64url');
  const session = await client.request('POST', '/api/v1/registration/verify', { id: options.id, challenge: options.challenge, walletSignature: signature(identity.privateKey), deviceSignature: signature(device.privateKey), ownerSignature: await owner.signMessage(options.message) });
  await privateWrite('session.json', JSON.stringify(session));
  client.session = session; client.privateKey = device.privateKey;
  const blob = await client.request('PUT', '/api/v1/blobs/backup', { expectedRevision: '0', cipherVersion: 1, ciphertext: Buffer.from(JSON.stringify(encryptedBackup)).toString('base64url') });
  const restored = await client.request('GET', '/api/v1/blobs/backup');
  if (restored.ciphertext !== blob.ciphertext) throw new Error('Remote ciphertext verification failed');
  const me = await client.request('GET', '/api/v1/me');
  const summary = { walletId: session.walletId, accountId: session.accountId, chainId: 8453, network: 'base', address: descriptor.address, assets: me.accounts[0].assets.map(asset => ({ contract: asset.contract, symbol: asset.symbol, address: asset.address })), backupRevision: blob.revision, localBackupVerified: true, remoteCiphertextVerified: true, deployedOnChain: false, transactionsSent: 0 };
  await privateWrite('public-account.json', JSON.stringify(summary, null, 2) + '\n');
  return { ...summary, privateFiles: location };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { console.log(JSON.stringify(await createAccount({ origin: process.env.MIDORI_API_ORIGIN || 'http://127.0.0.1:8080', directory: process.argv[2] }), null, 2)); }
  catch (error) { console.error(JSON.stringify({ event: 'account_creation_failed', code: error.code || 'CLIENT_ERROR', message: 'Check API availability and the private backup directory; existing files are never overwritten' })); process.exitCode = 1; }
}
