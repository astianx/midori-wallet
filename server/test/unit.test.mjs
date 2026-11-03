import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { loadConfig } from '../lib/config.js';
import { parseAtomic, validateDescriptor } from '../lib/descriptor.js';
import { verifyEd25519, requestMessage } from '../lib/crypto.js';
import { createWalletSession } from '../../packages/wallet-core/src/index.js';

test('configuration requires PostgreSQL and Base mainnet and rejects unsafe origins and enabled payments', () => {
  const env = { DATABASE_URL: 'postgresql://midori@localhost/midori' };
  assert.throws(() => loadConfig({}));
  for (const overrides of [{ DATABASE_URL: 'mongodb://localhost/midori' }, { MIDORI_CHAIN_ID: '84532' }, { MIDORI_NETWORK: 'base-sepolia' }, { MIDORI_API_PORT: 'invalid' }, { MIDORI_PUBLIC_ORIGIN: 'http://remote.example' }, { MIDORI_PUBLIC_ORIGIN: 'https://example.com/path' }, { MIDORI_RP_ID: 'evil.example' }, { MIDORI_TRANSACTIONS_ENABLED: 'true' }, { MIDORI_RPC_URL: 'http://remote.example' }]) assert.throws(() => loadConfig({ ...env, ...overrides }));
  assert.equal(loadConfig(env).poolSize, 10);
  assert.ok(loadConfig(env).allowedOrigins.includes('http://127.0.0.1:8080'));
  assert.deepEqual(loadConfig({ ...env, MIDORI_PUBLIC_ORIGIN: 'https://wallet.example' }).allowedOrigins, ['https://wallet.example']);
});

test('atomic values preserve precision and reject uint256 overflow, decimals and numbers', () => {
  const max = (2n ** 256n - 1n).toString();
  assert.equal(parseAtomic(max), max);
  for (const value of [1, -1, '-1', '1.01', '01', '1e6', (2n ** 256n).toString()]) assert.throws(() => parseAtomic(value));
});

test('server descriptor matches the actual F1 WDK derivation and rejects extra fields and drift', async () => {
  const wallet = await createWalletSession('abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about', { chainId: 8453 });
  try {
    const descriptor = wallet.descriptor;
    assert.equal(validateDescriptor(descriptor).address, descriptor.address);
    for (const bad of [{ ...descriptor, chainId: 84532 }, { ...descriptor, address: descriptor.ownerAddress }, { ...descriptor, privateKey: 'forbidden' }, { ...descriptor, factoryData: descriptor.factoryData.slice(0, -2) }, { ...descriptor, sdk: { ...descriptor.sdk, evm: 'unvalidated' } }]) assert.throws(() => validateDescriptor(bad));
  } finally { wallet.dispose(); }
});

test('signed requests bind body, session, nonce, timestamp, method and target', () => {
  const keys = generateKeyPairSync('ed25519');
  const publicKey = keys.publicKey.export({ type: 'spki', format: 'der' }).subarray(-32).toString('base64url');
  const request = { method: 'PUT', target: '/api/v1/blobs/metadata', timestamp: '1793664000', nonce: 'a'.repeat(32), token: 't'.repeat(43), body: '{"expectedRevision":"0"}' };
  const signature = sign(null, Buffer.from(requestMessage(request)), keys.privateKey).toString('base64url');
  assert.equal(verifyEd25519(publicKey, requestMessage(request), signature), true);
  for (const [key, value] of Object.entries({ method: 'DELETE', target: '/api/v1/blobs/backup', timestamp: '1793664001', nonce: 'b'.repeat(32), token: 'u'.repeat(43), body: '{}' })) assert.equal(verifyEd25519(publicKey, requestMessage({ ...request, [key]: value }), signature), false);
});
