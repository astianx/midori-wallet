import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { transakClient } from '../lib/transak.js';

const config = { transakEnvironment: 'STAGING', transakApiKey: 'test-key', transakApiSecret: 'test-secret', transakReferrerDomain: 'localhost' };
const order = { id: 'partner-order', kind: 'buy', crypto_currency: 'USDC', fiat_currency: 'USD', fiat_amount: '100.0000', crypto_amount: null, wallet_address: '0x' + '12'.repeat(20) };
const response = body => ({ ok: true, json: async () => body });
function provider(t, { widgetUrl = 'https://global-stg.transak.com/?sessionId=test', roundOff = 2, products = ['BUY','SELL'] } = {}) {
  const requests = [];
  t.mock.method(globalThis, 'fetch', async (url, init = {}) => {
    url = new URL(url); requests.push({ url, init });
    assert.ok(['api-stg.transak.com','api-gateway-stg.transak.com'].includes(url.hostname));
    if (url.pathname.endsWith('/partners/api/v2/test-key')) return response({ response: { productsAvailed: products } });
    if (url.pathname.includes('crypto-currencies')) return response({ response: [{ symbol: 'USDC', decimals: 18, roundOff, name: 'USD Coin', network: { name: 'base', chainId: '84532' }, isAllowed: true, isPayInAllowed: true }] });
    if (url.pathname.includes('fiat-currencies')) return response({ response: [{ symbol: 'USD', roundOff: 2, name: 'US Dollar', isAllowed: true, isPayOutAllowed: true }, { symbol: 'JPY', roundOff: 0, name: 'Yen', isAllowed: true, isPayOutAllowed: false }, { symbol: 'KWD', roundOff: 3, name: 'Dinar', isAllowed: true, isPayOutAllowed: true }] });
    if (url.pathname.endsWith('refresh-token')) return response({ data: { accessToken: 'shared-token', expiresAt: Math.floor(Date.now() / 1000) + 3600 } });
    if (url.pathname.endsWith('auth/session')) return response({ data: { widgetUrl } });
    if (url.pathname.endsWith('/orders')) return response({ data: [{ id: 'transak-id', partnerOrderId: 'partner-order', isBuyOrSell: 'SELL' }] });
    if (url.pathname.endsWith('/order/transak-id')) return response({ data: { id: 'transak-id', partnerOrderId: 'partner-order', status: 'COMPLETED', isBuyOrSell: 'SELL', cryptoCurrency: 'USDC', fiatCurrency: 'USD' } });
    throw new Error(`Unexpected test request ${url.pathname}`);
  });
  return { client: transakClient(config), requests };
}

test('WDK generates buy and sell sessions with staging asset decimals and keeps secrets in backend headers', async t => {
  const { client, requests } = provider(t);
  const options = await client.options();
  assert.equal(options.assets[0].decimals, 18);
  assert.equal(options.assets[0].chainId, '84532');
  assert.equal(options.fiatCurrencies.find(item => item.code === 'JPY').decimals, 0);
  await client.createWidget(order, '203.0.113.1');
  await client.createWidget({ ...order, kind: 'sell', fiat_amount: null, crypto_amount: '25.50' }, '203.0.113.2');
  const sessions = requests.filter(item => item.url.pathname.endsWith('auth/session'));
  const buy = JSON.parse(sessions[0].init.body).widgetParams;
  const sell = JSON.parse(sessions[1].init.body).widgetParams;
  assert.equal(buy.productsAvailed, 'BUY'); assert.equal(buy.fiatAmount, 100);
  assert.equal(buy.walletAddress, order.wallet_address); assert.equal(buy.disableWalletAddressForm, true);
  assert.equal(buy.partnerOrderId, order.id); assert.equal(buy.referrerDomain, 'localhost');
  assert.equal(sell.productsAvailed, 'SELL'); assert.equal(sell.cryptoAmount, 25.5);
  assert.equal(sell.walletAddress, undefined); assert.equal(sell.disableWalletAddressForm, undefined);
  assert.equal(sessions[0].init.headers['x-user-ip'], '203.0.113.1');
  assert.equal(sessions[1].init.headers['x-user-ip'], '203.0.113.2');
  assert.equal(requests.filter(item => item.url.pathname.endsWith('refresh-token')).length, 1);
  for (const params of [buy,sell]) { assert.ok(!JSON.stringify(params).includes('test-secret')); assert.ok(!JSON.stringify(params).includes('shared-token')); }
});

test('WDK rejects unsupported staging assets, payouts and amounts it would round', async t => {
  const { client, requests } = provider(t);
  for (const invalid of [
    { ...order, crypto_currency: 'USDT' },
    { ...order, kind: 'sell', fiat_currency: 'JPY', fiat_amount: null, crypto_amount: '25' },
    { ...order, fiat_currency: 'JPY', fiat_amount: '100.01' },
    { ...order, kind: 'sell', fiat_amount: null, crypto_amount: '25.123456' },
  ]) await assert.rejects(client.createWidget(invalid, '203.0.113.1'), { code: 'INVALID_REQUEST' });
  assert.equal(requests.filter(item => item.url.pathname.endsWith('auth/session')).length, 0);
  await client.createWidget({ ...order, fiat_currency: 'KWD', fiat_amount: '25.123' }, '203.0.113.1');
  assert.equal(JSON.parse(requests.at(-1).init.body).widgetParams.fiatAmount, 25.123);
});

test('widget callback rejects a URL outside the selected Transak environment', async t => {
  const { client } = provider(t, { widgetUrl: 'https://attacker.example/?sessionId=test' });
  await assert.rejects(client.createWidget(order, '203.0.113.1'), { code: 'RAMP_PROVIDER_UNAVAILABLE' });
});

test('sell reconciliation filters SELL explicitly and loads authoritative details through WDK', async t => {
  const { client, requests } = provider(t);
  const result = await client.findOrderByPartnerOrderId('partner-order', 'sell');
  const listing = requests.find(item => item.url.pathname.endsWith('/orders')).url;
  assert.equal(listing.searchParams.get('filter[productsAvailed]'), '["SELL"]');
  assert.equal(listing.searchParams.get('filter[partnerOrderId]'), 'partner-order');
  assert.equal(result.status, 'COMPLETED'); assert.equal(result.partnerOrderId, 'partner-order');
  assert.ok(requests.some(item => item.url.pathname.endsWith('/order/transak-id')));
});

test('webhook verification rejects expired and not-yet-valid claims and non-HS256 headers', async t => {
  const { client } = provider(t);
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const sign = (header, payload) => { const input = `${encode(header)}.${encode(payload)}`; return `${input}.${createHmac('sha256', 'shared-token').update(input).digest('base64url')}`; };
  assert.equal(await client.verifyWebhook(sign({ alg: 'none' }, { webhookData: {} })), null);
  assert.equal(await client.verifyWebhook(sign({ alg: 'HS256' }, { exp: 1 })), null);
  assert.equal(await client.verifyWebhook(sign({ alg: 'HS256' }, { nbf: Math.floor(Date.now() / 1000) + 3600 })), null);
  assert.equal(await client.verifyWebhook(sign({ alg: 'HS256' }, null)), null);
});

test('partner product permissions disable selling even when asset coverage allows off-ramp', async t => {
  const { client, requests } = provider(t, { products: ['BUY'] });
  const options = await client.options();
  assert.deepEqual(options.products, ['BUY']);
  assert.equal(options.assets[0].buy, true); assert.equal(options.assets[0].sell, false);
  assert.ok(options.fiatCurrencies.every(fiat => fiat.sell === false));
  await assert.rejects(client.createWidget({ ...order, kind: 'sell', fiat_amount: null, crypto_amount: '25' }, '203.0.113.1'), { code: 'INVALID_REQUEST' });
  assert.equal(requests.filter(item => item.url.pathname.endsWith('auth/session')).length, 0);
});
