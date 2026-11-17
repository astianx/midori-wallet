import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify, createHash } from 'node:crypto';
import { changellyClient, changellyCredentials } from '../lib/changelly.js';
import { loadConfig } from '../lib/config.js';

const pair = generateKeyPairSync('rsa', { modulusLength: 2048 });
const secret = pair.privateKey.export({ type: 'pkcs8', format: 'der' }).toString('hex');
function client(handler) {
  const requests = [];
  return { requests, api: changellyClient({ changellyApiSecret: secret }, async (url, init) => {
    assert.equal(url, 'https://api.changelly.com/v2');
    assert.ok(verify('sha256', Buffer.from(init.body), pair.publicKey, Buffer.from(init.headers['X-Api-Signature'], 'base64')));
    assert.equal(init.headers['X-Api-Key'], createHash('sha256').update(pair.publicKey.export({ type: 'pkcs1', format: 'der' })).digest('base64'));
    assert.ok(!init.body.includes(secret)); assert.equal(init.redirect, 'error');
    const message = JSON.parse(init.body); requests.push(message);
    const result = await handler(message);
    return { ok: true, json: async () => ({ jsonrpc: '2.0', id: message.id, result }) };
  }) };
}
test('Changelly signs the exact serialized request with RSA SHA256 and derives its API key', async () => {
  const { api, requests } = client(() => [{ ticker: 'usdcbase', fullName: 'USDC Base', enabled: true, enabledFrom: true, enabledTo: true, blockchain: 'BASE', contractAddress: '0x' + 'AB'.repeat(20), blockchainPrecision: 6 }, { ticker: 'disabled', enabled: false }]);
  const [first, second] = await Promise.all([api.currencies(), api.currencies()]);
  assert.deepEqual(first, second); assert.equal(requests.length, 1); assert.equal(first.length, 1); assert.equal(first[0].network, 'BASE'); assert.equal(first[0].contract, '0x' + 'ab'.repeat(20));
  assert.equal(changellyCredentials(secret).apiKey, createHash('sha256').update(pair.publicKey.export({ type: 'pkcs1', format: 'der' })).digest('base64'));
});
test('exchange estimate subtracts payout network fee exactly and preserves unknown fees', async () => {
  const { api, requests } = client(() => [{ from: 'usdcbase', to: 'usdt20', amountTo: '10000000000000000.123456789012345678', networkFee: '0.000000000000000001' }]);
  const estimate = await api.estimate('usdcbase', 'usdt20', '100');
  assert.equal(estimate.amountTo, '10000000000000000.123456789012345677'); assert.equal(estimate.fee, null);
  assert.deepEqual(requests[0].params, [{ from: 'usdcbase', to: 'usdt20', amountFrom: '100' }]);
});
test('exchange rejects empty estimates, invalid network fees and fees exceeding proceeds', async () => {
  await assert.rejects(client(() => []).api.estimate('usdcbase', 'usdt20', '100'), { code: 'EXCHANGE_PAIR_UNAVAILABLE' });
  await assert.rejects(client(() => [{ from: 'a', to: 'b', amountTo: '10', networkFee: '-1' }]).api.estimate('a', 'b', '100'), { code: 'EXCHANGE_PROVIDER_UNAVAILABLE' });
  await assert.rejects(client(() => [{ from: 'a', to: 'b', amountTo: '1', networkFee: '2' }]).api.estimate('a', 'b', '100'), { code: 'EXCHANGE_AMOUNT_UNAVAILABLE' });
});
test('exchange validates destinations and only retrieves the requested transaction', async () => {
  const { api } = client(message => message.method === 'validateAddress' ? { result: false } : [{ id: 'foreign' }]);
  assert.equal(await api.validate('usdt20', 'bad-address'), false);
  await assert.rejects(api.transaction('own'), { code: 'EXCHANGE_PROVIDER_UNAVAILABLE' });
});
test('provider transport and JSON-RPC errors expose a generic error without credentials', async () => {
  for (const fetchImpl of [async () => { throw Error(secret); }, async () => ({ ok: false, json: async () => ({ error: { message: secret } }) }), async () => ({ ok: true, json: async () => ({ jsonrpc: '2.0', id: 'wrong-id', result: [] }) })]) {
    await assert.rejects(changellyClient({ changellyApiSecret: secret }, fetchImpl).currencies(), error => error.code === 'EXCHANGE_PROVIDER_UNAVAILABLE' && !error.message.includes(secret));
  }
});
test('Changelly is explicitly enabled and malformed private keys fail configuration', () => {
  const env = { DATABASE_URL: 'postgresql://localhost/midori_test', MIDORI_PUBLIC_ORIGIN: 'http://localhost:5173' };
  assert.equal(loadConfig(env).changellyEnabled, false);
  assert.throws(() => loadConfig({ ...env, CHANGELLY_ENABLED: 'yes' }));
  assert.throws(() => loadConfig({ ...env, CHANGELLY_ENABLED: 'true', CHANGELLY_API_SECRET: 'invalid' }));
  assert.equal(loadConfig({ ...env, CHANGELLY_ENABLED: 'true', CHANGELLY_API_SECRET: secret }).changellyEnabled, true);
});
