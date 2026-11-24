import test from 'node:test';
import assert from 'node:assert/strict';
import AssetCatalog from '../../web/src/lib/account/AssetCatalog.js';
import { Amount } from '../../web/src/lib/account/domain.js';
import { stablecoinFamily, stablecoinPositions, canonicalStablecoin, groupStablecoins } from '../../web/src/lib/account/stablecoin-accounts.js';
import { portfolioReferenceValue } from '../../web/src/lib/account/portfolio.js';
const catalog = new AssetCatalog();
const anchors = ['USDC', 'USDT'].map(family => canonicalStablecoin(catalog.all, family));
const positions = stablecoinPositions(anchors, catalog.all);
const item = (crypto, value = '1') => ({ crypto, platform: catalog.platform(crypto.platform), balance: Amount.fromString(value, crypto.decimals), readState: 'fresh' });
test('stablecoin accounts initialize all network positions once without mutating saved asset metadata', () => {
  assert.equal(positions.length, 9); assert.equal(anchors.length, 2);
  assert.deepEqual(anchors.map(asset => asset.platform), ['base', 'ethereum']);
  assert.equal(stablecoinPositions(positions, catalog.all).length, 9);
  assert.equal(positions.filter(asset => stablecoinFamily(asset) === 'USDC').length, 5);
  const legacy = catalog.all.find(asset => asset.platform === 'base' && asset.symbol === 'USDT');
  assert.ok(stablecoinPositions([...anchors, legacy], catalog.all).some(asset => asset._id === legacy._id));
});
test('changing the selected network keeps one row per stablecoin and preserves exact totals', () => {
  const rows = positions.map(crypto => item(crypto, '9007199254740993.123456'));
  for (const active of positions) {
    const grouped = groupStablecoins(rows, active._id);
    assert.equal(grouped.length, 2);
    const group = grouped.find(row => row.family === stablecoinFamily(active));
    assert.equal(group.crypto._id, active._id);
    assert.equal(group.balance.value, 9007199254740993123456n * BigInt(group.positions.length));
  }
  assert.equal(portfolioReferenceValue(rows), portfolioReferenceValue(groupStablecoins(rows)));
});
test('missing balances stay unknown and stale readings are not shown as current', () => {
  const rows = positions.map(crypto => item(crypto));
  const usdc = rows.find(row => row.crypto.symbol === 'USDC');
  usdc.balance = undefined;
  const unknown = groupStablecoins(rows).find(row => row.family === 'USDC');
  assert.equal(unknown.balance, undefined); assert.equal(unknown.readState, 'unavailable');
  usdc.balance = Amount.fromString('1'); usdc.readState = 'stale';
  const cached = groupStablecoins(rows).find(row => row.family === 'USDC');
  assert.equal(cached.balance.toString(), '5'); assert.equal(cached.readState, 'stale');
  const bitcoin = item(catalog.get('bitcoin@bitcoin'), '0.00000001');
  const groupedBitcoin = groupStablecoins([...rows, bitcoin]).at(-1);
  assert.equal(groupedBitcoin.family, 'BTC'); assert.equal(groupedBitcoin.balance.value, bitcoin.balance.value);
});

test('Bitcoin and Spark share one visible Bitcoin without combining their addresses or spending balances', () => {
  const bitcoin = item(catalog.get('bitcoin@bitcoin'), '0.12345678');
  const spark = item(catalog.get('bitcoin@spark'), '0.00000001');
  bitcoin.market = spark.market = { usdPrice: 80000 };
  for (const selected of [bitcoin, spark]) {
    const groups = groupStablecoins([bitcoin, spark], selected.crypto._id);
    assert.equal(groups.length, 1); assert.equal(groups[0].title, 'Bitcoin');
    assert.equal(groups[0].family, 'BTC'); assert.equal(groups[0].crypto._id, selected.crypto._id);
    assert.equal(groups[0].balance.toString(), '0.12345679');
    assert.equal(groups[0].positions.length, 2);
    assert.equal(portfolioReferenceValue(groups), portfolioReferenceValue([bitcoin, spark]));
  }
  spark.balance = undefined;
  assert.equal(groupStablecoins([bitcoin, spark])[0].balance, undefined);
});
