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
import { createApp } from '../app.js';
import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { migrate } from '../lib/migrations.js';
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
  assert.equal(initial.length, 4);
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
