import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, randomBytes, randomUUID, sign } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { Wallet } from 'ethers';
import { isoCBOR } from '@simplewebauthn/server/helpers';
import pg from 'pg';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { createWalletSession } from '../../packages/wallet-core/src/index.js';
import { transakClient } from '../lib/transak.js';
import { createApp } from '../app.js';
import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { migrate, migrationFiles } from '../lib/migrations.js';
import { requestMessage } from '../lib/crypto.js';
import { deviceEnrollmentMessage } from '../lib/wallets.js';
import { MidoriClient } from '../lib/client.js';
import { maintainExpirations } from '../worker.js';
import { grantRuntime } from '../scripts/bootstrap.mjs';
import { MidoriClient as BrowserClient, EncryptedBlobSync } from '../../web/src/lib/api/MidoriClient.js';

const enabled = !!process.env.MIDORI_TEST_DATABASE_URL;
const scenario = (name, action) => test(name, { skip: !enabled }, action);
const makeKey = () => {
  const pair = generateKeyPairSync('ed25519');
  return { ...pair, public: pair.publicKey.export({ type: 'spki', format: 'der' }).subarray(-32).toString('base64url') };
};
const signature = (pair, message) => sign(null, Buffer.from(message), pair.privateKey).toString('base64url');
let db, apiDb, server, origin, config, alice, bob, ajv, spec;

before(async () => {
  if (!enabled) return;
  const url = new URL(process.env.MIDORI_TEST_DATABASE_URL);
  if (!/^\/midori_verify_[a-z0-9]+$/.test(url.pathname)) throw new Error('Integration checks require an explicitly isolated midori_verify_* database');
  config = loadConfig({ DATABASE_URL: url.href, MIDORI_RATE_LIMIT: '10000', MIDORI_PUBLIC_ORIGIN: 'http://localhost:5173' });
  db = createDatabase(config);
  const initial = await migrate(db);
  assert.deepEqual(initial, (await migrationFiles()).map(file => file.version));
  assert.deepEqual(await migrate(db), []);
  if (process.env.MIDORI_TEST_RUNTIME_DATABASE_URL) {
    const runtimeUrl = new URL(process.env.MIDORI_TEST_RUNTIME_DATABASE_URL);
    await grantRuntime(db, runtimeUrl.username);
    apiDb = createDatabase(loadConfig({ DATABASE_URL: runtimeUrl.href, MIDORI_PUBLIC_ORIGIN: 'http://localhost:5173' }));
  }
  server = createServer(createApp({ db: apiDb || db, config }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  origin = `http://127.0.0.1:${server.address().port}`;
  spec = JSON.parse(await readFile(new URL('../openapi.json', import.meta.url), 'utf8'));
  ajv = new Ajv2020({ strict: false }); addFormats(ajv);
  ajv.addSchema({ $id: 'midori-contract', ...spec });
});
after(async () => {
  if (server) await new Promise(resolve => server.close(resolve));
  if (apiDb) await apiDb.close();
  if (db) await db.close();
});

function validateResponse(method, target, status, value) {
  const path = Object.keys(spec.paths).find(path => new RegExp('^' + path.replace(/\{[^}]+\}/g, '[^/]+') + '$').test(target));
  const schema = spec.paths[path][method.toLowerCase()].responses[status]?.content['application/json'].schema;
  assert.ok(schema, `Documented ${method} ${target} ${status}`);
  const qualified = JSON.parse(JSON.stringify(schema).replaceAll('#/components/schemas/', 'midori-contract#/components/schemas/'));
  const validate = ajv.compile(qualified);
  assert.ok(validate(value), JSON.stringify(validate.errors));
}

async function publicCall(target, body) {
  const response = await fetch(origin + target, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await response.json(); validateResponse('POST', target, response.status, data);
  return { status: response.status, data };
}

async function identity() {
  const root = makeKey(); const device = makeKey(); const owner = Wallet.createRandom();
  const wallet = await createWalletSession(owner.mnemonic.phrase, { chainId: 8453 });
  const descriptor = wallet.descriptor; wallet.dispose();
  const body = { walletPublicKey: root.public, devicePublicKey: device.public, descriptor };
  const { data: options } = await publicCall('/api/v1/registration/options', body);
  const proof = { id: options.id, challenge: options.challenge, walletSignature: signature(root, options.message), deviceSignature: signature(device, options.message), ownerSignature: await owner.signMessage(options.message) };
  const result = await publicCall('/api/v1/registration/verify', proof);
  assert.equal(result.status, 201);
  const client = new MidoriClient({ origin, privateKey: device.privateKey, session: result.data, fetchImpl: async (url, init) => {
    const response = await fetch(url, init); validateResponse(init.method, url.pathname, response.status, await response.clone().json()); return response;
  } });
  return { root, device, owner, descriptor, proof, body, session: result.data, client };
}

scenario('empty PostgreSQL bootstrap, idempotence, health, public contract and two real HTTP registrations', async () => {
  const health = await fetch(origin + '/healthz'); assert.equal(health.status, 200); validateResponse('GET', '/healthz', 200, await health.json());
  assert.equal((await fetch(origin + '/readyz')).status, 200);
  assert.equal((await fetch(origin + '/api/v1/config', { headers: { Origin: 'http://127.0.0.1:8080' } })).status, 200);
  assert.equal((await fetch(origin + '/api/v1/config', { headers: { Origin: 'https://untrusted.example' } })).status, 403);
  const publicConfig = await fetch(origin + '/api/v1/config').then(r => r.json()); validateResponse('GET', '/api/v1/config', 200, publicConfig);
  assert.equal(publicConfig.network.chainId, 8453);
  assert.doesNotMatch(JSON.stringify(publicConfig), /DATABASE_URL|rpcUrl|password|paymasterUrl/);
  alice = await identity(); bob = await identity();
  const me = await alice.client.request('GET', '/api/v1/me');
  assert.equal(me.accounts.length, 1); assert.equal(me.accounts[0].assets.length, 2);
  assert.equal(new Set(me.accounts[0].assets.map(asset => asset.address)).size, 1);
  assert.ok(me.accounts[0].assets.every(asset => asset.balanceAtomic === null));
  assert.equal((await publicCall('/api/v1/registration/verify', alice.proof)).status, 401);
});

scenario('API role can recover identity and enroll devices without UPDATE rights on wallets', async () => {
  if (!apiDb) return;
  const role = (await apiDb.query('SELECT rolsuper,rolcreatedb,rolcreaterole FROM pg_roles WHERE rolname=current_user')).rows[0];
  assert.ok(Object.values(role).every(value => value === false));
  await assert.rejects(apiDb.query('UPDATE wallets SET id=id WHERE false'), { code: '42501' });
});

scenario('duplicate identities and fake contracts cannot create another account', async () => {
  const { data: options } = await publicCall('/api/v1/registration/options', alice.body);
  const duplicate = await publicCall('/api/v1/registration/verify', { id: options.id, challenge: options.challenge, walletSignature: signature(alice.root, options.message), deviceSignature: signature(alice.device, options.message), ownerSignature: await alice.owner.signMessage(options.message) });
  assert.equal(duplicate.status, 409);
  assert.equal((await publicCall('/api/v1/registration/options', { ...alice.body, descriptor: { ...alice.descriptor, privateKey: 'forbidden' } })).status, 400);
  await assert.rejects(db.query('INSERT INTO account_assets(account_id,chain_id,contract) VALUES ($1,8453,$2)', [alice.session.accountId, '0x' + '1'.repeat(40)]), { code: '23503' });
  await assert.rejects(db.query('INSERT INTO account_assets(account_id,chain_id,contract) VALUES ($1,8453,$2)', [alice.session.accountId, 'USDT']), { code: '23503' });
});

scenario('PostgreSQL rejects numeric overflow and fractions; API preserves exact amounts as strings', async () => {
  const contract = '0xfde4c96c8593536e31f229ea8f37b2ada2699bb2';
  const max = (2n ** 256n - 1n).toString();
  await db.query('UPDATE account_assets SET balance_atomic=$2,observed_block=123 WHERE account_id=$1 AND contract=$3', [alice.session.accountId, max, contract]);
  const saved = await alice.client.request('GET', `/api/v1/accounts/${alice.session.accountId}`);
  assert.equal(saved.assets.find(asset => asset.symbol === 'USDT').balanceAtomic, max);
  for (const value of ['-1', '0.1', (2n ** 256n).toString()]) await assert.rejects(db.query('UPDATE account_assets SET balance_atomic=$2 WHERE account_id=$1', [alice.session.accountId, value]), { code: '23514' });
});

scenario('foreign account access, revoked sessions and revoked device challenges fail', async () => {
  await assert.rejects(alice.client.request('GET', `/api/v1/accounts/${bob.session.accountId}`), { status: 404 });
  const device = makeKey();
  const enrolled = await alice.client.request('POST', '/api/v1/devices', { publicKey: device.public, signature: signature(device, deviceEnrollmentMessage(alice.session.walletId, device.public)) });
  const { data: options } = await publicCall('/api/v1/auth/options', { deviceId: enrolled.id });
  const proof = { id: options.id, challenge: options.challenge, signature: signature(device, options.message) };
  const logged = await publicCall('/api/v1/auth/verify', proof); assert.equal(logged.status, 200);
  const deviceClient = new MidoriClient({ origin, privateKey: device.privateKey, session: logged.data });
  await deviceClient.request('GET', '/api/v1/me');
  await alice.client.request('DELETE', `/api/v1/devices/${enrolled.id}`);
  await assert.rejects(deviceClient.request('GET', '/api/v1/me'), { status: 401 });
  assert.equal((await publicCall('/api/v1/auth/verify', proof)).status, 401);
});

scenario('expired and consumed challenges fail without running the cleanup worker; concurrent login succeeds once', async () => {
  const first = await publicCall('/api/v1/auth/options', { deviceId: alice.session.deviceId });
  await db.query("UPDATE auth_challenges SET expires_at=clock_timestamp()-interval '1 second' WHERE id=$1", [first.data.id]);
  assert.equal((await publicCall('/api/v1/auth/verify', { id: first.data.id, challenge: first.data.challenge, signature: signature(alice.device, first.data.message) })).status, 401);
  const { data: options } = await publicCall('/api/v1/auth/options', { deviceId: alice.session.deviceId });
  const proof = { id: options.id, challenge: options.challenge, signature: signature(alice.device, options.message) };
  const results = await Promise.all([publicCall('/api/v1/auth/verify', proof), publicCall('/api/v1/auth/verify', proof)]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 401]);
  const wrong = await publicCall('/api/v1/auth/options', { deviceId: alice.session.deviceId });
  assert.equal((await publicCall('/api/v1/auth/verify', { id: wrong.data.id, challenge: wrong.data.challenge, signature: signature(bob.device, wrong.data.message) })).status, 401);
  assert.equal((await publicCall('/api/v1/auth/verify', { id: wrong.data.id, challenge: wrong.data.challenge, signature: signature(alice.device, wrong.data.message) })).status, 401);
});

scenario('request replay and tampered body fail and expired sessions are invalid before cleanup', async () => {
  const timestamp = String(Math.floor(Date.now() / 1000)); const nonce = randomBytes(24).toString('base64url'); const target = '/api/v1/me';
  const message = requestMessage({ method: 'GET', target, timestamp, nonce, token: alice.session.token });
  const headers = { Authorization: `Bearer ${alice.session.token}`, 'X-Midori-Timestamp': timestamp, 'X-Midori-Nonce': nonce, 'X-Midori-Signature': signature(alice.device, message) };
  assert.equal((await fetch(origin + target, { headers })).status, 200);
  assert.equal((await fetch(origin + target, { headers })).status, 409);
  assert.equal((await fetch(origin + '/api/v1/devices', { headers })).status, 401);
  await db.query("UPDATE sessions SET expires_at=clock_timestamp()-interval '1 second' WHERE id=$1", [bob.session.sessionId]);
  await assert.rejects(bob.client.request('GET', '/api/v1/me'), { status: 401 });
});

scenario('concurrent encrypted writes produce one visible conflict; deletion never resets revision', async () => {
  const ciphertext = randomBytes(128).toString('base64url');
  const first = await alice.client.request('PUT', '/api/v1/blobs/metadata', { expectedRevision: '0', cipherVersion: 1, ciphertext }); assert.equal(first.revision, '1');
  const writes = await Promise.allSettled([1, 2].map(() => alice.client.request('PUT', '/api/v1/blobs/metadata', { expectedRevision: '1', cipherVersion: 1, ciphertext })));
  assert.equal(writes.filter(x => x.status === 'fulfilled').length, 1);
  assert.equal(writes.find(x => x.status === 'rejected').reason.code, 'REVISION_CONFLICT');
  const deleted = await alice.client.request('DELETE', '/api/v1/blobs/metadata', { expectedRevision: '2' }); assert.equal(deleted.revision, '3'); assert.equal(deleted.ciphertext, null);
  await assert.rejects(alice.client.request('PUT', '/api/v1/blobs/metadata', { expectedRevision: '0', cipherVersion: 1, ciphertext }), { status: 409 });
  assert.equal((await alice.client.request('PUT', '/api/v1/blobs/metadata', { expectedRevision: '3', cipherVersion: 1, ciphertext })).revision, '4');
  const broken = new MidoriClient({ origin, privateKey: alice.device.privateKey, session: alice.session, fetchImpl: async () => { throw new TypeError('Network failed'); } });
  await assert.rejects(broken.request('PUT', '/api/v1/blobs/metadata', { expectedRevision: '4', cipherVersion: 1, ciphertext }), /Network failed/);
  assert.equal((await alice.client.request('GET', '/api/v1/blobs/metadata')).revision, '4');
});

scenario('root recovery registers a distinct device without changing the shared Base account', async () => {
  const device = makeKey();
  const { data: options } = await publicCall('/api/v1/recovery/options', { walletPublicKey: alice.root.public, devicePublicKey: device.public });
  const result = await publicCall('/api/v1/recovery/verify', { id: options.id, challenge: options.challenge, walletSignature: signature(alice.root, options.message), deviceSignature: signature(device, options.message) });
  assert.equal(result.status, 201); assert.equal(result.data.walletId, alice.session.walletId);
  const client = new MidoriClient({ origin, privateKey: device.privateKey, session: result.data });
  assert.equal((await client.request('GET', '/api/v1/me')).accounts[0].address, alice.descriptor.address);
});

scenario('shared client keeps unconfirmed writes pending and explicitly reconciles lost HTTP acknowledgements', async () => {
  const client = new BrowserClient({ origin, session: alice.session, signMessage: async message => new Uint8Array(sign(null, message, alice.device.privateKey)) });
  const sync = new EncryptedBlobSync(client);
  assert.equal(await sync.read('client_backup'), null);
  await sync.write('client_backup', { ciphertext: randomBytes(64).toString('base64url') });
  assert.equal(sync.revision('client_backup'), '1');
  client.fetchImpl = async (url, init) => { await fetch(url, init); throw new TypeError('Response lost after commit'); };
  await assert.rejects(sync.write('client_backup', { ciphertext: randomBytes(64).toString('base64url') }), { code: 'NETWORK_UNCONFIRMED' });
  assert.equal(sync.revision('client_backup'), '1');
  client.fetchImpl = fetch;
  assert.equal((await sync.read('client_backup')).revision, '2');
  assert.equal(sync.revision('client_backup'), '2');
});

function authenticator() {
  const pair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const jwk = pair.publicKey.export({ format: 'jwk' });
  const credentialId = randomBytes(32);
  const rpHash = createHash('sha256').update(config.rpID).digest();
  const digest = bytes => createHash('sha256').update(bytes).digest();
  const clientData = (type, challenge) => Buffer.from(JSON.stringify({ type, challenge, origin: config.publicOrigin, crossOrigin: false }));
  return {
    id: credentialId.toString('base64url'),
    registration(challenge) {
      const cose = isoCBOR.encode(new Map([[1, 2], [3, -7], [-1, 1], [-2, Buffer.from(jwk.x, 'base64url')], [-3, Buffer.from(jwk.y, 'base64url')]]));
      const length = Buffer.alloc(2); length.writeUInt16BE(credentialId.length);
      const authData = Buffer.concat([rpHash, Buffer.from([0x45]), Buffer.alloc(4), Buffer.alloc(16), length, credentialId, Buffer.from(cose)]);
      return { id: this.id, rawId: this.id, type: 'public-key', clientExtensionResults: {}, response: { clientDataJSON: clientData('webauthn.create', challenge).toString('base64url'), attestationObject: Buffer.from(isoCBOR.encode(new Map([['fmt', 'none'], ['attStmt', new Map()], ['authData', authData]]))).toString('base64url'), transports: ['internal'] } };
    },
    assertion(challenge, counter) {
      const count = Buffer.alloc(4); count.writeUInt32BE(counter);
      const authData = Buffer.concat([rpHash, Buffer.from([0x05]), count]);
      const client = clientData('webauthn.get', challenge);
      return { id: this.id, rawId: this.id, type: 'public-key', clientExtensionResults: {}, response: { clientDataJSON: client.toString('base64url'), authenticatorData: authData.toString('base64url'), signature: sign('sha256', Buffer.concat([authData, digest(client)]), pair.privateKey).toString('base64url') } };
    },
  };
}

scenario('real WebAuthn signature verification persists newCounter and rejects concurrent counter updates', async () => {
  const credential = authenticator();
  const options = await alice.client.request('POST', '/api/v1/authenticators/options', {});
  await alice.client.request('POST', '/api/v1/authenticators/verify', { id: options.id, challenge: options.challenge, response: credential.registration(options.challenge) });
  const one = (await publicCall('/api/v1/auth/webauthn/options', { deviceId: alice.session.deviceId })).data;
  const two = (await publicCall('/api/v1/auth/webauthn/options', { deviceId: alice.session.deviceId })).data;
  const results = await Promise.all([publicCall('/api/v1/auth/webauthn/verify', { id: one.id, challenge: one.challenge, response: credential.assertion(one.challenge, 1) }), publicCall('/api/v1/auth/webauthn/verify', { id: two.id, challenge: two.challenge, response: credential.assertion(two.challenge, 2) })]);
  assert.deepEqual(results.map(r => r.status).sort(), [200, 401]);
  const saved = await db.query('SELECT counter,revision FROM authenticators WHERE credential_id=$1', [credential.id]);
  assert.ok(['1', '2'].includes(saved.rows[0].counter)); assert.equal(saved.rows[0].revision, '2');
  const stale = (await publicCall('/api/v1/auth/webauthn/options', { deviceId: alice.session.deviceId })).data;
  assert.equal((await publicCall('/api/v1/auth/webauthn/verify', { id: stale.id, challenge: stale.challenge, response: credential.assertion(stale.challenge, 0) })).status, 401);
  await alice.client.request('DELETE', `/api/v1/authenticators/${credential.id}`);
  assert.equal((await publicCall('/api/v1/auth/webauthn/options', { deviceId: alice.session.deviceId })).status, 401);
});

scenario('buy/sell ramp orders are idempotent, track status from a verified webhook, and stay disabled by default', async () => {
  assert.equal((await alice.client.request('GET', '/api/v1/config')).capabilities.ramp, false);
  await assert.rejects(alice.client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, { idempotencyKey: randomUUID(), kind: 'buy', cryptoCurrency: 'USDT', fiatCurrency: 'USD', fiatAmount: '100' }), { status: 503, code: 'RAMP_UNAVAILABLE' });
  const rampConfig = loadConfig({ DATABASE_URL: process.env.MIDORI_TEST_DATABASE_URL, MIDORI_RATE_LIMIT: '10000', MIDORI_PUBLIC_ORIGIN: 'http://localhost:5173', MIDORI_TRANSAK_ENABLED: 'true', MIDORI_TRANSAK_API_KEY: 'test-partner-key', MIDORI_TRANSAK_API_SECRET: 'test-partner-secret', MIDORI_TRANSAK_REFERRER_DOMAIN: 'midoriwallet.com' });
  const sessions = [];
  const rampProvider = {
    async options() { return { products: ['BUY','SELL'], environment: 'STAGING', network: 'base', assets: [{ symbol: 'USDT', decimals: 6, roundOff: 6, chainId: '8453', buy: true, sell: true }], fiatCurrencies: [{ code: 'USD', decimals: 2, buy: true, sell: true }] }; },
    async createWidget(order, userIp) {
      assert.equal(userIp, '127.0.0.1');
      const widgetParams = { partnerOrderId: order.id, walletAddress: order.kind === 'buy' ? order.wallet_address : undefined, disableWalletAddressForm: order.kind === 'buy' };
      sessions.push(widgetParams);
      return `https://global-stg.transak.com/?apiKey=test-partner-key&sessionId=fake-${sessions.length}`;
    },
    async findOrderByPartnerOrderId() { return null; },
    // Test-only encoding (base64url JSON); the real HS256 JWT verification
    // in lib/transak.js is covered directly in unit.test.mjs.
    async verifyWebhook(token) { try { return JSON.parse(Buffer.from(token, 'base64url').toString('utf8')); } catch { return null; } },
  };
  const rampServer = createServer(createApp({ db: apiDb || db, config: rampConfig, rampProvider }));
  await new Promise(resolve => rampServer.listen(0, '127.0.0.1', resolve));
  try {
    const rampOrigin = `http://127.0.0.1:${rampServer.address().port}`;
    const client = new MidoriClient({ origin: rampOrigin, privateKey: alice.device.privateKey, session: alice.session, fetchImpl: async (url, init) => {
      const response = await fetch(url, { ...init, headers: { ...init.headers, 'x-forwarded-for': '198.51.100.99' } }); validateResponse(init.method, url.pathname, response.status, await response.clone().json()); return response;
    } });
    assert.equal((await client.request('GET', '/api/v1/config')).capabilities.ramp, true);
    assert.equal((await client.request('GET', '/api/v1/ramp/options')).environment, 'STAGING');
    const idempotencyKey = randomUUID();
    const request = { idempotencyKey, kind: 'buy', cryptoCurrency: 'USDT', fiatCurrency: 'USD', fiatAmount: '100' };
    const order = await client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, request);
    assert.equal(order.status, 'created'); assert.equal(order.walletAddress.toLowerCase(), alice.descriptor.address.toLowerCase()); assert.match(order.widgetUrl, /^https:\/\/global-stg\.transak\.com\/\?apiKey=test-partner-key&sessionId=fake-1$/);
    assert.equal(sessions[0].partnerOrderId, order.id); assert.equal(sessions[0].walletAddress.toLowerCase(), alice.descriptor.address.toLowerCase()); assert.equal(sessions[0].disableWalletAddressForm, true);
    const repeated = await client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, request);
    assert.equal(repeated.id, order.id); assert.match(repeated.widgetUrl, /sessionId=fake-2$/);
    await assert.rejects(client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, { ...request, idempotencyKey, fiatAmount: '200' }), { status: 409, code: 'IDEMPOTENCY_CONFLICT' });
    for (const invalid of [{ ...request, fiatAmount: undefined }, { ...request, cryptoAmount: '10' }, { ...request, fiatAmount: '0.001' }, { ...request, cryptoCurrency: 'ETH' }]) await assert.rejects(client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, invalid), { status: 400 });
    await assert.rejects(client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, { ...request, paymentMethod: 'credit_debit_card' }), { status: 409 });
    await assert.rejects(client.request('POST', `/api/v1/accounts/${bob.session.accountId}/ramp/orders`, request), { status: 404 });
    assert.deepEqual((await client.request('GET', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`)).orders.map(item => item.id), [order.id]);
    assert.equal((await client.request('GET', `/api/v1/ramp/orders/${order.id}`)).status, 'created');
    await assert.rejects(client.request('GET', `/api/v1/ramp/orders/${bob.session.accountId}`), { status: 404 });
    const sign = payload => Buffer.from(JSON.stringify(payload)).toString('base64url');
    const webhook = async data => {
      const response = await fetch(rampOrigin + '/api/v1/webhooks/transak', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ data }) });
      const body = await response.json(); validateResponse('POST', '/api/v1/webhooks/transak', response.status, body); return { status: response.status, body };
    };
    assert.equal((await webhook('not-a-jwt')).status, 401);
    const processing = await webhook(sign({ eventID: 'ORDER_PROCESSING', webhookData: { partnerOrderId: order.id, status: 'PROCESSING' } }));
    assert.equal(processing.status, 200); assert.equal((await client.request('GET', `/api/v1/ramp/orders/${order.id}`)).status, 'processing');
    await webhook(sign({ eventID: 'ORDER_PAYMENT_PENDING', webhookData: { partnerOrderId: order.id, status: 'AWAITING_PAYMENT_FROM_USER' } }));
    assert.equal((await client.request('GET', `/api/v1/ramp/orders/${order.id}`)).status, 'processing');
    await webhook(sign({ eventID: 'ORDER_COMPLETED', webhookData: { partnerOrderId: order.id, status: 'COMPLETED', cryptoCurrency: 'USDC' } }));
    assert.equal((await client.request('GET', `/api/v1/ramp/orders/${order.id}`)).status, 'processing');
    const completed = await webhook(sign({ eventID: 'ORDER_COMPLETED', webhookData: { partnerOrderId: order.id, status: 'COMPLETED', id: 'transak-order-1', transactionHash: '0x' + 'ab'.repeat(32) } }));
    assert.equal(completed.status, 200);
    const final = await client.request('GET', `/api/v1/ramp/orders/${order.id}`);
    assert.equal(final.status, 'completed'); assert.equal(final.transactionHash, '0x' + 'ab'.repeat(32));
    // A terminal order is final: a later event for the same order is accepted but ignored.
    await webhook(sign({ eventID: 'ORDER_FAILED', webhookData: { partnerOrderId: order.id, status: 'FAILED' } }));
    assert.equal((await client.request('GET', `/api/v1/ramp/orders/${order.id}`)).status, 'completed');
    await assert.rejects(client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, request), { status: 409 });
    await webhook(sign({ eventID: 'ORDER_REFUNDED', webhookData: { partnerOrderId: order.id, status: 'REFUNDED' } }));
    assert.equal((await client.request('GET', `/api/v1/ramp/orders/${order.id}`)).status, 'refunded');
    await assert.rejects(client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, { idempotencyKey: randomUUID(), kind: 'sell', cryptoCurrency: 'USDT', fiatCurrency: 'USD' }), { status: 400 });
    const sell = await client.request('POST', `/api/v1/accounts/${alice.session.accountId}/ramp/orders`, { idempotencyKey: randomUUID(), kind: 'sell', cryptoCurrency: 'USDT', fiatCurrency: 'USD', cryptoAmount: '25.50' });
    assert.equal(sell.environment, 'STAGING'); assert.equal(sell.kind, 'sell');
    assert.equal(sessions.at(-1).walletAddress, undefined);
    assert.equal(sessions.at(-1).disableWalletAddressForm, false);
  } finally { await new Promise(resolve => rampServer.close(resolve)); }
});

scenario('API instances and workers share one Transak token across concurrent calls and restarts', async () => {
  let refreshes = 0;
  const fakeFetch = async url => {
    if (String(url).endsWith('/refresh-token')) { refreshes++; return { ok: true, json: async () => ({ data: { accessToken: `token-${refreshes}`, expiresAt: Math.floor(Date.now() / 1000) + 3600 } }) }; }
    return { ok: true, json: async () => ({ data: { widgetUrl: 'https://global-stg.transak.com/?sessionId=test' } }) };
  };
  const cfg = { transakEnvironment: 'STAGING', transakApiKey: 'shared-key', transakApiSecret: 'shared-secret' };
  const clients = Array.from({ length: 3 }, () => transakClient(cfg, fakeFetch, apiDb || db));
  await Promise.all(clients.map(client => client.widgetSession({}, '127.0.0.1')));
  assert.equal(refreshes, 1);
  await transakClient(cfg, fakeFetch, apiDb || db).widgetSession({}, '127.0.0.1');
  assert.equal(refreshes, 1);
  await db.query('UPDATE transak_tokens SET expires_at=1');
  await Promise.all(clients.map(client => client.widgetSession({}, '127.0.0.1')));
  assert.equal(refreshes, 2);
});

scenario('Changelly discovers real network identities, binds quotes to owners and never recreates an uncertain order', async () => {
  const { exchangeService } = await import('../lib/exchange.js');
  const usdc = '0x833589fcd6edb6e08f4c7c32d4f71b54bda02913';
  const source = { ticker: 'usdcbase', name: 'USD Coin Base', network: 'BASE', contract: usdc, decimals: 6, sell: true, buy: true, extraIdName: null, notification: null };
  const destination = { ticker: 'usdtbase', name: 'Tether Base', network: 'base', contract: '0xfde4c96c8593536e31f229ea8f37b2ada2699bb2', decimals: 6, sell: true, buy: true, extraIdName: null, notification: null };
  let creates = 0, loseResponse = false, invalidAddress = false, wrongOrder = false;
  const payout = alice.descriptor.address, deposit = '0x' + '12'.repeat(20);
  const provider = {
    currencies: async () => [source, destination, { ...source, ticker: 'fake', contract: '0x' + 'ab'.repeat(20) }, { ...source, ticker: 'wrong-network', network: 'ethereum' }],
    estimate: async () => ({ amountTo: '98.75', networkFee: '0.25', fee: '1', min: '20', max: '10000' }),
    validate: async () => !invalidAddress,
    create: async params => { creates++; if (loseResponse) throw Error('timeout'); assert.equal(params.refundAddress.toLowerCase(), alice.descriptor.address.toLowerCase()); return { id: 'changelly-' + creates, currencyFrom: params.from, currencyTo: params.to, payoutAddress: params.address, payinAddress: deposit }; },
    transaction: async id => ({ id, currencyFrom: 'usdcbase', currencyTo: 'usdtbase', status: 'finished', payinAddress: deposit, payoutAddress: wrongOrder ? 'foreign' : payout, amountTo: '98.73', payoutHash: 'a'.repeat(64) }),
  };
  const cfg = { ...config, changellyEnabled: true, paymentsEnabled: true };
  const service = exchangeService(apiDb || db, cfg, provider), walletId = alice.session.walletId, accountId = alice.session.accountId;
  const options = await service.options(walletId, accountId);
  assert.deepEqual(options.providers[0].sources.map(item => item.ticker), ['usdcbase','usdtbase']);
  const input = { from: 'usdcbase', to: 'usdtbase', amount: '100' };
  await assert.rejects(service.quote(walletId, bob.session.accountId, input), { status: 404 });
  await assert.rejects(service.quote(walletId, accountId, { ...input, from: 'fake' }), { status: 400 });
  await assert.rejects(service.quote(walletId, accountId, { ...input, amount: '100.0000001' }), { status: 400 });
  const quote = await service.quote(walletId, accountId, input);
  validateResponse('POST', `/api/v1/accounts/${accountId}/exchange/quotes`, 200, JSON.parse(JSON.stringify(quote)));
  const request = { quoteId: quote.id, idempotencyKey: randomUUID(), payoutAddress: payout };
  await assert.rejects(service.create({ wallet_id: walletId }, accountId, { ...request, payoutAddress: '0x' + '56'.repeat(20) }), { code: 'INVALID_REQUEST' });
  await assert.rejects(service.quote(walletId, accountId, { ...input, to: 'wrong-network' }), { code: 'INVALID_REQUEST' });
  await assert.rejects(service.create({ wallet_id: bob.session.walletId }, bob.session.accountId, request), { code: 'EXCHANGE_QUOTE_EXPIRED' });
  invalidAddress = true; await assert.rejects(service.create({ wallet_id: walletId }, accountId, request), { code: 'INVALID_REQUEST' }); assert.equal(creates, 0); invalidAddress = false;
  const order = await service.create({ wallet_id: walletId }, accountId, request);
  validateResponse('POST', `/api/v1/accounts/${accountId}/exchange/orders`, 200, JSON.parse(JSON.stringify(order)));
  assert.equal(order.status, 'waiting'); assert.equal(order.depositAddress.toLowerCase(), deposit);
  assert.equal((await service.create({ wallet_id: walletId }, accountId, request)).id, order.id); assert.equal(creates, 1);
  await assert.rejects(service.create({ wallet_id: walletId }, accountId, { ...request, payoutAddress: 'changed' }), { code: 'IDEMPOTENCY_CONFLICT' });
  await assert.rejects(service.get(bob.session.walletId, order.id), { status: 404 });
  const { paymentService } = await import('../lib/payments.js');
  const paymentConfig = { ...cfg, gasRecipients: [], maxGasFeeAtomic: '1000000' };
  const payments = paymentService(apiDb || db, paymentConfig, { balances: async () => ({ assets: { [usdc]: 1000000000n }, blockNumber: '1' }), quote: async () => ({ maxFeeAtomic: '1000', estimatedFeeAtomic: '900' }) });
  const principal = { wallet_id: walletId, device_id: alice.session.deviceId, session_id: alice.session.sessionId };
  const depositRequest = { idempotencyKey: randomUUID(), exchangeOrderId: order.id, contract: usdc, amountAtomic: '100000000', recipient: { kind: 'address', address: deposit }, feeMode: 'token', feeToken: usdc, maxFeeAtomic: '1000000' };
  await assert.rejects(payments.create(principal, accountId, { ...depositRequest, amountAtomic: '99000000' }), { code: 'INVALID_REQUEST' });
  await assert.rejects(payments.create(principal, accountId, { ...depositRequest, recipient: { kind: 'address', address: payout } }), { code: 'INVALID_REQUEST' });
  const payment = await payments.create(principal, accountId, depositRequest);
  assert.equal(payment.status, 'quoted');
  await assert.rejects(payments.create(principal, accountId, { ...depositRequest, idempotencyKey: randomUUID() }), error => error.code === 'PAYMENT_ALREADY_PENDING' && error.details.intentId === payment.id);
  assert.equal((await service.list(walletId, accountId)).orders[0].paymentIntentId, payment.id);
  wrongOrder = true; await assert.rejects(service.get(walletId, order.id), { code: 'EXCHANGE_PROVIDER_UNAVAILABLE' }); wrongOrder = false;
  assert.equal((await service.get(walletId, order.id)).status, 'finished');
  assert.equal((await service.get(walletId, order.id)).amountTo, '98.73');
  const second = await service.quote(walletId, accountId, input); loseResponse = true;
  const retry = { ...request, quoteId: second.id, idempotencyKey: randomUUID() };
  assert.equal((await service.create({ wallet_id: walletId }, accountId, retry)).status, 'uncertain');
  assert.equal((await service.create({ wallet_id: walletId }, accountId, retry)).status, 'uncertain'); assert.equal(creates, 2);
  assert.equal((await service.list(walletId, accountId)).orders.length, 2);
  const expired = await service.quote(walletId, accountId, input); await db.query("UPDATE exchange_quotes SET expires_at=clock_timestamp()-interval '1 second' WHERE id=$1", [expired.id]);
  await assert.rejects(service.create({ wallet_id: walletId }, accountId, { ...request, quoteId: expired.id, idempotencyKey: randomUUID() }), { code: 'EXCHANGE_QUOTE_EXPIRED' });
});

scenario('native Bitcoin binding and swaps enforce ownership, precision and internal payouts', async () => {
  const { withBitcoinAccount, bitcoinBindingMessage } = await import('../../packages/wallet-core/src/index.js');
  const { exchangeService } = await import('../lib/exchange.js');
  const user = await identity(), client = user.client, walletId = user.session.walletId, accountId = user.session.accountId;
  const offline = { async connect() {}, async close() {} };
  const address = await withBitcoinAccount(user.owner.mnemonic.phrase, offline, account => account.getAddress());
  const message = bitcoinBindingMessage(walletId, accountId, user.descriptor.address, address);
  const proof = { address, signature: await withBitcoinAccount(user.owner.mnemonic.phrase, offline, account => account.sign(message)), ownerSignature: await user.owner.signMessage(message) };
  assert.equal((await client.request('PUT', `/api/v1/accounts/${accountId}/bitcoin`, proof)).address, address);
  assert.equal((await client.request('PUT', `/api/v1/accounts/${accountId}/bitcoin`, proof)).address, address);
  await assert.rejects(client.request('PUT', `/api/v1/accounts/${bob.session.accountId}/bitcoin`, proof), { status: 404 });
  await assert.rejects(client.request('PUT', `/api/v1/accounts/${accountId}/bitcoin`, { ...proof, signature: 'invalid' }), { status: 400 });
  const deposit = await withBitcoinAccount(new Uint8Array(64).fill(2), offline, account => account.getAddress());
  const btc = { ticker: 'btc', name: 'Bitcoin', network: 'bitcoin', contract: null, decimals: 8, buy: true, sell: true, extraIdName: null, notification: null };
  const usdc = { ticker: 'usdcbase', name: 'USD Coin', network: 'base', contract: '0x833589fcd6edb6e08f4c7c32d4f71b54bda02913', decimals: 6, buy: true, sell: true, extraIdName: null, notification: null };
  const provider = { currencies: async () => [btc, usdc], estimate: async () => ({ amountTo: '80', networkFee: '1', fee: null, min: null, max: null }), validate: async () => true,
    create: async params => { assert.equal(params.refundAddress, address); return { id: 'bitcoin-proof', currencyFrom: 'btc', currencyTo: 'usdcbase', payoutAddress: params.address, payinAddress: deposit }; } };
  const exchange = exchangeService(apiDb || db, { ...config, changellyEnabled: true, paymentsEnabled: false }, provider);
  const quote = await exchange.quote(walletId, accountId, { from: 'btc', to: 'usdcbase', amount: '0.00123456' });
  assert.equal(quote.amountFrom, '0.00123456');
  await assert.rejects(exchange.quote(walletId, accountId, { from: 'btc', to: 'usdcbase', amount: '0.001234567' }), { status: 400 });
  const request = { quoteId: quote.id, idempotencyKey: randomUUID(), payoutAddress: user.descriptor.address };
  await assert.rejects(exchange.create({ wallet_id: walletId }, accountId, { ...request, payoutAddress: bob.descriptor.address }), { status: 400 });
  const order = await exchange.create({ wallet_id: walletId }, accountId, request);
  assert.equal(order.status, 'waiting'); assert.equal(order.depositAddress, deposit); assert.equal(order.payoutAddress, user.descriptor.address);
  assert.equal((await exchange.create({ wallet_id: walletId }, accountId, request)).id, order.id);
});

scenario('Changelly Fiat persists owned destinations, exact sell amounts, retries and uncertain recovery', async () => {
  const { changellyFiatService } = await import('../lib/changelly-fiat-service.js');
  const user = await identity(), walletId=user.session.walletId, accountId=user.session.accountId;
  const address='bc1qr6ddtxn4k8jmg75ejgqg525wwzmtur7zy0nnr0';
  await db.query('INSERT INTO bitcoin_accounts(wallet_id,account_id,address) VALUES($1,$2,$3)',[walletId,accountId,address]);
  const support=[{providerCode:'moonpay',supportedFlows:['buy','sell']}], records=new Map(); let creates=0,loseResponse=false;
  const provider={countries:async()=>[{code:'CO',name:'Colombia',providers:support}],currencies:async()=>[{type:'crypto',ticker:'BTC',network:'bitcoin',precision:'8',providers:support},{type:'fiat',ticker:'USD',precision:'2',providers:support}],offers:async()=>[{providerCode:'moonpay',paymentMethodOffer:[{method:'card',amountExpectedTo:'0.001',fee:'2'}]}],create:async(kind,value)=>{creates++;const payload=JSON.parse(JSON.stringify(value));if(kind==='buy')assert.equal(payload.walletAddress,address);else{assert.equal(payload.refundAddress,address);assert.equal(payload.amountFrom,0.00123456);}const response={...payload,amountFrom:String(payload.amountFrom),type:kind,orderId:randomUUID(),redirectUrl:'https://buy.moonpay.com/changelly?order='+payload.externalOrderId};records.set(payload.externalOrderId,response);if(loseResponse)throw Error('response lost');return response;},orders:async id=>({orders:records.has(id)?[{...records.get(id),status:'complete',payoutAmount:'0.001'}]:[]})};
  const cfg={...config,changellyFiatEnabled:true,changellyFiatEnvironment:'PRODUCTION'},service=changellyFiatService(db,cfg,provider);
  const body={kind:'buy',cryptoCurrency:'BTC',network:'bitcoin',fiatCurrency:'USD',country:'CO',amount:'100.00'};
  await assert.rejects(service.quote(walletId,accountId,{...body,walletAddress:bob.descriptor.address},'127.0.0.1'),{status:400});
  await assert.rejects(service.options(bob.session.walletId,accountId,{kind:'buy',cryptoCurrency:'BTC',network:'bitcoin'}),{status:404});
  const quote=(await service.quote(walletId,accountId,body,'127.0.0.1')).quotes[0];assert.equal(quote.walletAddress,address);
  const request={quoteId:quote.id,idempotencyKey:randomUUID()},order=await service.create(walletId,accountId,request,'127.0.0.1');assert.equal(order.status,'awaiting_payment');assert.equal(order.provider,'changelly');
  assert.equal((await service.create(walletId,accountId,request,'127.0.0.1')).id,order.id);assert.equal(creates,1);assert.equal((await service.get(walletId,order.id)).status,'completed');
  const sell=(await service.quote(walletId,accountId,{...body,kind:'sell',amount:'0.00123456'},'127.0.0.1')).quotes[0];loseResponse=true;
  const unknown=await service.create(walletId,accountId,{quoteId:sell.id,idempotencyKey:randomUUID()},'127.0.0.1');assert.equal(unknown.status,'ambiguous');
  await assert.rejects(service.create(walletId,accountId,{quoteId:sell.id,idempotencyKey:randomUUID()},'127.0.0.1'),{code:'IDEMPOTENCY_CONFLICT'});assert.equal(creates,2);
  assert.equal((await service.get(walletId,unknown.id)).status,'completed');assert.equal(creates,2);
  const other=changellyFiatService(db,{...cfg,changellyFiatEnabled:false},provider);assert.equal((await other.list(walletId,accountId)).orders.length,2);await assert.rejects(other.quote(walletId,accountId,body,'127.0.0.1'),{code:'RAMP_UNAVAILABLE'});
});

scenario('Spark swaps use native BTC provider tickers and reserve a single withdrawal',async()=>{
  const {exchangeService}=await import('../lib/exchange.js');const user=await identity(),walletId=user.session.walletId,accountId=user.session.accountId;
  const spark='spark1pgssyp5hy794jwrc8gz3eavz5ucaangm3w26ewwv7dtyw68mf2vgptgkt5uc3g',deposit='bc1pms2y66vsjyexsx963nme4rf6hqfc2hnnhvr2r0pqknvln4m5tqkquyg09k';
  await db.query('INSERT INTO spark_accounts(wallet_id,account_id,address,deposit_address) VALUES($1,$2,$3,$4)',[walletId,accountId,spark,deposit]);
  const btc={ticker:'btc',name:'Bitcoin',network:'bitcoin',contract:null,decimals:8,buy:true,sell:true,extraIdName:null,notification:null},usdc={...btc,ticker:'usdcbase',network:'base',contract:'0x833589fcd6edb6e08f4c7c32d4f71b54bda02913',decimals:6};let params;
  const provider={currencies:async()=>[btc,usdc],estimate:async(from,to)=>{assert.equal(from,'btc');assert.equal(to,'usdcbase');return{amountTo:'80',networkFee:'1'};},validate:async()=>true,create:async value=>{params=value;return{id:'spark-bridge',currencyFrom:'btc',currencyTo:'usdcbase',payoutAddress:value.address,payinAddress:'bc1qr6ddtxn4k8jmg75ejgqg525wwzmtur7zy0nnr0'};}};
  const service=exchangeService(db,{...config,changellyEnabled:true,paymentsEnabled:false},provider),options=await service.options(walletId,accountId);assert.equal(options.providers[0].destinations.find(item=>item.ticker==='btc:spark').address,deposit);
  const q=await service.quote(walletId,accountId,{from:'btc:spark',to:'usdcbase',amount:'0.00123456'}),o=await service.create({wallet_id:walletId},accountId,{quoteId:q.id,idempotencyKey:randomUUID(),payoutAddress:user.descriptor.address});assert.equal(o.status,'waiting');assert.equal(params.from,'btc');assert.equal(params.refundAddress,deposit);
  const operationId=randomUUID();assert.equal((await service.reserveSpark(walletId,o.id,{operationId})).depositReference,operationId);await assert.rejects(service.reserveSpark(walletId,o.id,{operationId:randomUUID()}),{status:400});await assert.rejects(service.reserveSpark(bob.session.walletId,o.id,{operationId}),{status:400});
});

scenario('maintenance removes expired rows; client and API report unavailable PostgreSQL without claiming saved data', async () => {
  const removed = await maintainExpirations(db); assert.ok(removed.auth_challenges > 0 && removed.sessions > 0);
  const admin = new pg.Client({ connectionString: process.env.MIDORI_PG_ADMIN_URL }); await admin.connect();
  const name = new URL(process.env.MIDORI_TEST_DATABASE_URL).pathname.slice(1);
  try {
    await admin.query(`ALTER DATABASE "${name}" ALLOW_CONNECTIONS false`);
    await admin.query('SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=$1', [name]);
    assert.equal((await fetch(origin + '/healthz')).status, 200);
    assert.equal((await fetch(origin + '/readyz')).status, 503);
    const response = await fetch(origin + '/api/v1/assets'); assert.equal(response.status, 503);
    validateResponse('GET', '/api/v1/assets', 503, await response.json());
    await assert.rejects(alice.client.request('PUT', '/api/v1/blobs/metadata', { expectedRevision: '4', cipherVersion: 1, ciphertext: randomBytes(64).toString('base64url') }), { status: 503 });
  } finally {
    await admin.query(`ALTER DATABASE "${name}" ALLOW_CONNECTIONS true`); await admin.end();
  }
  assert.equal((await fetch(origin + '/readyz')).status, 200);
  assert.equal((await alice.client.request('GET', '/api/v1/blobs/metadata')).revision, '4');
});
