import test from 'node:test';
import assert from 'node:assert/strict';
import { loadConfig } from '../lib/config.js';
import { createWdkIndexer, inspectWdkIndexer, loadWdkIndexerConfig } from '../lib/wdk-indexer.js';

const config = () => loadWdkIndexerConfig({ WDK_INDEXER_API_KEY: 'test-server-secret' });
const response = body => Response.json(body);

test('WDK configuration uses production and rejects credentials in URLs, paths, headers and invalid timeouts', () => {
  const loaded = config();
  assert.equal(loaded.baseUrl, 'https://wdk-api.tether.io');
  assert.equal(loaded.timeoutMs, 30000);
  assert.ok(Object.isFrozen(loaded));
  assert.equal(loadWdkIndexerConfig({}).apiKey, '');
  for (const baseUrl of ['http://wdk-api.tether.io', 'https://user:password@wdk-api.tether.io', 'https://wdk-api.tether.io/api/v1', 'https://wdk-api.tether.io?key=secret', 'https://wdk-api.tether.io/#fragment']) assert.throws(() => loadWdkIndexerConfig({ WDK_INDEXER_BASE_URL: baseUrl }));
  for (const timeout of ['999', '60001', '1.5', 'invalid']) assert.throws(() => loadWdkIndexerConfig({ WDK_INDEXER_TIMEOUT_MS: timeout }));
  assert.throws(() => loadWdkIndexerConfig({ WDK_INDEXER_API_KEY: 'key\r\nx-other: value' }));
  const app = loadConfig({ DATABASE_URL: 'postgresql://midori@localhost/midori', MIDORI_PUBLIC_ORIGIN: 'http://localhost:5173', WDK_INDEXER_API_KEY: loaded.apiKey });
  assert.equal(app.wdkIndexer.apiKey, loaded.apiKey);
});

test('public chain discovery omits the key; authenticated verification sends it only as a header', async () => {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, ...init });
    return response(url.endsWith('/chains') ? { chains: [{ name: 'ethereum', tokens: ['usdt'] }] } : { wallets: [] });
  };
  const report = await inspectWdkIndexer(config(), fetchImpl);
  assert.equal(report.authenticated, true);
  assert.equal(report.coverageComplete, false);
  assert.deepEqual(report.unsupportedTokens, ['usdt', 'usdc']);
  assert.equal(report.historyProvider, 'rpc');
  assert.equal(calls[0].url, 'https://wdk-api.tether.io/api/v1/chains');
  assert.equal(calls[0].headers['x-api-key'], undefined);
  assert.equal(calls[1].url, 'https://wdk-api.tether.io/api/v1/wallets');
  assert.equal(calls[1].headers['x-api-key'], config().apiKey);
  for (const call of calls) {
    assert.equal(call.method, 'GET');
    assert.equal(call.redirect, 'error');
    assert.equal(call.body, undefined);
    assert.ok(call.signal instanceof AbortSignal);
    assert.ok(!call.url.includes(config().apiKey));
  }
  assert.ok(!JSON.stringify(report).includes(config().apiKey));
});

test('a missing key makes no requests and cannot authenticate using a public endpoint', async () => {
  const fetchImpl = () => assert.fail('Unexpected network request');
  assert.deepEqual(await inspectWdkIndexer(loadWdkIndexerConfig({}), fetchImpl), { configured: false, authenticated: false, historyProvider: 'rpc' });
  await assert.rejects(createWdkIndexer(loadWdkIndexerConfig({}), fetchImpl).verifyAccess(), { code: 'WDK_INDEXER_NOT_CONFIGURED' });
});

test('coverage is discovered for both Base tokens without assuming Ethereum or testnet coverage applies', async () => {
  for (const tokens of [[], ['usdt'], ['usdc'], ['usdt', 'usdc']]) {
    const report = await inspectWdkIndexer(config(), async url => response(url.endsWith('/chains') ? { chains: [{ name: 'base', tokens }] } : { wallets: [] }));
    assert.equal(report.coverageComplete, tokens.length === 2);
    assert.deepEqual(report.unsupportedTokens, ['usdt', 'usdc'].filter(token => !tokens.includes(token)));
    assert.equal(report.historyProvider, 'rpc');
  }
});

test('invalid inventory and authentication response shapes cannot appear successful', async () => {
  for (const body of [null, {}, { chains: null }, { chains: [{ name: '../base', tokens: [] }] }, { chains: [{ name: 'base', tokens: [null] }] }]) await assert.rejects(createWdkIndexer(config(), async () => response(body)).getChains(), { code: 'WDK_INDEXER_INVALID_RESPONSE' });
  await assert.rejects(createWdkIndexer(config(), async () => response({})).verifyAccess(), { code: 'WDK_INDEXER_INVALID_RESPONSE' });
  await assert.rejects(createWdkIndexer(config(), async () => new Response('not json')).getChains(), { code: 'WDK_INDEXER_INVALID_RESPONSE' });
});

test('access errors and throttling are sanitized and never automatically retried', async () => {
  for (const status of [401, 403, 429, 500]) {
    let calls = 0;
    const client = createWdkIndexer(config(), async () => {
      calls++;
      return new Response(config().apiKey, { status, headers: { 'retry-after': '12' } });
    });
    await assert.rejects(client.verifyAccess(), error => {
      assert.ok(!error.message.includes(config().apiKey));
      assert.equal(error.code, status === 429 ? 'WDK_INDEXER_RATE_LIMITED' : status === 500 ? 'WDK_INDEXER_UNAVAILABLE' : 'WDK_INDEXER_ACCESS_DENIED');
      if (status === 429) assert.equal(error.details.retryAfter, 12);
      return true;
    });
    assert.equal(calls, 1);
  }
});

test('network failures do not include upstream errors that could contain a secret', async () => {
  const client = createWdkIndexer(config(), async () => { throw new Error(config().apiKey); });
  await assert.rejects(client.getChains(), error => error.code === 'WDK_INDEXER_UNAVAILABLE' && !error.message.includes(config().apiKey));
});

test('timeout remains active while the response body is being read', async () => {
  const client = createWdkIndexer({ ...config(), timeoutMs: 10 }, async (url, { signal }) => ({
    ok: true, status: 200,
    json: () => new Promise((resolve, reject) => {
      const timer = setTimeout(resolve, 200, { chains: [] });
      signal.addEventListener('abort', () => { clearTimeout(timer); reject(signal.reason); }, { once: true });
    }),
  }));
  await assert.rejects(client.getChains(), { code: 'WDK_INDEXER_TIMEOUT' });
});
