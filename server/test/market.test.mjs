import test from 'node:test';
import assert from 'node:assert/strict';
import { marketService } from '../lib/market.js';
import { createApp } from '../app.js';
import { loadConfig } from '../lib/config.js';
import AccountMarket from '../../web/src/lib/account/AccountMarket.js';
import { validateAssets, BASE_ASSETS } from '../../web/src/lib/account/AssetCatalog.js';

const timestamp = Date.parse('2026-11-16T15:00:00Z');
const prices = [[timestamp - 3600000, 0.99991], [timestamp, 1.00002]];

test('history preserves stablecoin precision, isolates currency/period and shares concurrent/cache requests', async () => {
  const calls = [];
  let current = timestamp;
  const market = marketService({ apiKey: 'demo-key', now: () => current, fetchImpl: async (url, options) => {
    calls.push({ url, options });
    return Response.json({ prices });
  } });
  const [one, two] = await Promise.all([market.chart('USDC', {}), market.chart('USDC', {})]);
  assert.deepEqual(one, two);
  assert.deepEqual(one.prices, prices);
  assert.equal(one.updatedAt, '2026-11-16T15:00:00.000Z');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url.pathname, '/api/v3/coins/usd-coin/market_chart');
  assert.equal(calls[0].options.headers['x-cg-demo-api-key'], 'demo-key');
  await market.chart('USDC', {});
  assert.equal(calls.length, 1);
  await market.chart('USDT', { currency: 'EUR', period: '1Y' });
  assert.equal(calls[1].url.pathname, '/api/v3/coins/tether/market_chart');
  assert.equal(calls[1].url.searchParams.get('vs_currency'), 'eur');
  assert.equal(calls[1].url.searchParams.get('days'), '365');
  current += 300001;
  await market.chart('USDC', {});
  assert.equal(calls.length, 3);
});

test('unsupported requests never reach the upstream', async () => {
  const market = marketService({ fetchImpl: () => { throw new Error('Must not fetch'); } });
  for (const [symbol, query] of [['BTC', {}], ['__proto__', {}], ['USDC', { period: 'max' }], ['USDC', { period: ['1D'] }], ['USDC', { currency: 'XXX' }], ['USDT', { currency: ['USD', 'EUR'] }], ['USDT', { url: 'https://example.com' }]]) {
    await assert.rejects(market.chart(symbol, query), { status: 400, code: 'INVALID_REQUEST' });
  }
});

test('bad/empty/stale histories and upstream errors stay unavailable, recover and never reuse expired data', async () => {
  for (const result of [[], [[timestamp, 0]], [[timestamp, -1], [timestamp + 1, 1]], [[timestamp, 1], [timestamp, 1]], [[timestamp, '1'], [timestamp + 1, 1]], [[timestamp - 172800001, 1], [timestamp - 172800000, 1]], [[timestamp, 1], [timestamp + 86400000, 1]]]) {
    const market = marketService({ now: () => timestamp, fetchImpl: async () => Response.json({ prices: result }) });
    await assert.rejects(market.chart('USDC', {}), { status: 503, code: 'MARKET_UNAVAILABLE' });
  }
  let current = timestamp;
  let calls = 0;
  const market = marketService({ now: () => current, fetchImpl: async () => ++calls === 2 ? new Response('', { status: 429 }) : Response.json({ prices }) });
  await market.chart('USDT', {});
  current += 300001;
  await assert.rejects(market.chart('USDT', {}), { code: 'MARKET_UNAVAILABLE' });
  await assert.rejects(market.chart('USDT', {}), { code: 'MARKET_UNAVAILABLE' });
  assert.equal(calls, 2);
  current += 30001;
  assert.deepEqual((await market.chart('USDT', {})).prices, prices);
  assert.equal(calls, 3);
});

test('account history uses the public Midori client and maps exact registry assets', async () => {
  const calls = [];
  const client = new AccountMarket({ assets: validateAssets(BASE_ASSETS), publicClient: { request: async (...args) => { calls.push(args); return { prices }; } } });
  const assets = validateAssets(BASE_ASSETS);
  for (const asset of assets) assert.deepEqual(await client.getChartData(asset._id, '7D', 'EUR'), { prices });
  assert.deepEqual(calls, assets.map(asset => ['GET', `/api/v1/market/${asset.symbol}/chart?period=7D&currency=EUR`]));
  await assert.rejects(client.getChartData('bitcoin@bitcoin', '1D', 'USD'));
  assert.equal(await client.getMarket(assets[0]._id, 'USD'), undefined);
});

test('public API returns chart data without identity or database access and preserves origin checks', async () => {
  const config = loadConfig({ DATABASE_URL: 'postgres://test:test@127.0.0.1/test', MIDORI_PUBLIC_ORIGIN: 'http://127.0.0.1:5173' });
  const db = { query: () => { throw new Error('Price history must not query the database'); } };
  const marketProvider = marketService({ now: () => timestamp, fetchImpl: async () => Response.json({ prices }) });
  const app = createApp({ db, config, marketProvider, reportError() {} });
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  try {
    const response = await fetch(`${origin}/api/v1/market/USDC/chart?period=7D&currency=EUR`);
    assert.equal(response.status, 200);
    assert.deepEqual((await response.json()).prices, prices);
    assert.equal((await fetch(`${origin}/api/v1/market/BTC/chart`)).status, 400);
    assert.equal((await fetch(`${origin}/api/v1/market/USDT/chart`, { headers: { Origin: 'https://untrusted.example' } })).status, 403);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});
