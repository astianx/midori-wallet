import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
const { Transaction, address: btcAddress, networks } = createRequire(new URL('../../packages/wallet-core/package.json', import.meta.url))('bitcoinjs-lib');
import { BitcoinClient, bitcoinReader, withBitcoinAccount, validateBitcoinAddress, bitcoinBindingMessage, bitcoinTransactionId, bitcoinOutputs, createWalletSession } from '../../packages/wallet-core/src/index.js';
import { bitcoinGateway, registerBitcoin, broadcastBitcoin } from '../lib/bitcoin.js';
import { exchangeService } from '../lib/exchange.js';

const noNetwork = new BitcoinClient(() => { throw new Error('Network forbidden'); });
const seed = new Uint8Array(64).fill(1);
const descriptor = await withBitcoinAccount(seed, noNetwork, async account => ({ address: await account.getAddress(), path: account.path }));
test('WDK restores the same native SegWit address and verifies its ownership offline', async () => {
  assert.equal(descriptor.path, "m/84'/0'/0'/0/0");
  assert.equal(descriptor.address, 'bc1qr6ddtxn4k8jmg75ejgqg525wwzmtur7zy0nnr0');
  await withBitcoinAccount(seed, noNetwork, async account => {
    assert.equal(await account.getAddress(), descriptor.address);
    const signature = await account.sign('Midori ownership');
    assert.equal(await bitcoinReader(descriptor.address, noNetwork).verify('Midori ownership', signature), true);
    assert.equal(await bitcoinReader(descriptor.address, noNetwork).verify('Changed', signature), false);
  });
  for (const value of ['0x' + '12'.repeat(20), 'bc1qinvalid', descriptor.address + ' ', 'tb1qfm5vrx5hwlfnun9zmg2gn4zpywwq6c6kmmdjkp']) assert.equal(validateBitcoinAddress(value), false);
});
test('WDK signs a Bitcoin spend offline, preserves eight decimals and caps fees', async () => {
  const to = await withBitcoinAccount(new Uint8Array(64).fill(2), noNetwork, account => account.getAddress());
  const funding = new Transaction(); funding.addInput(new Uint8Array(32), 0xffffffff);
  funding.addOutput(btcAddress.toOutputScript(descriptor.address, networks.bitcoin), 1000000n);
  const client = { async connect() {}, async close() {}, async listUnspent() { return [{ tx_hash: funding.getId(), tx_pos: 0, value: 1000000, height: 1 }]; }, async getTransaction() { return funding.toHex(); }, async getBalance() { return { confirmed: 1000000 }; }, async estimateFee() { return 0.00002; } };
  const quote = await bitcoinReader(descriptor.address, client).quoteSendTransaction({ to, value: 12345n, feeRate: 2n });
  const raw = await withBitcoinAccount(seed, client, account => account.signTransaction({ to, value: 12345n, feeRate: 2n }), quote.fee);
  assert.equal(bitcoinOutputs(raw).find(output => output.address === to).value, '12345');
  assert.match(bitcoinTransactionId(raw), /^[a-f0-9]{64}$/);
  assert.equal(Transaction.fromHex(raw).ins[0].witness.length, 2);
  await assert.rejects(withBitcoinAccount(seed, client, account => account.signTransaction({ to, value: 12345n, feeRate: 2n }), 1n));
});
test('Bitcoin gateway accepts only fixed Esplora resources and hides upstream failures', async () => {
  const calls = [];
  const gateway = bitcoinGateway(async url => { calls.push(url); return { ok: true, json: async () => ({ confirmed: true }) }; });
  assert.deepEqual(await gateway(`address/${descriptor.address}`), { confirmed: true });
  assert.equal(calls[0], `https://blockstream.info/api/address/${descriptor.address}`);
  await gateway(`address/${descriptor.address}`); assert.equal(calls.length, 1);
  for (const path of ['https://example.com', '../secrets', 'address/invalid', 'tx/invalid']) await assert.rejects(gateway(path), { code: 'INVALID_REQUEST' });
  await assert.rejects(bitcoinGateway(async () => { throw new Error('credential'); })(`address/${descriptor.address}`), error => error.code === 'CHAIN_UNAVAILABLE' && !error.message.includes('credential'));
});
test('Bitcoin registration requires both owners and cannot be rebound', async () => {
  const walletId = randomUUID(), accountId = randomUUID(); const base = await createWalletSession(seed, { chainId: 8453 });
  let bound;
  const db = { async query(sql, args) {
    if (sql.startsWith('SELECT id,descriptor')) return { rows: [{ id: accountId, descriptor: base.descriptor }] };
    if (sql.includes('FROM account_assets')) return { rows: [] };
    if (sql.startsWith('INSERT INTO bitcoin_accounts')) bound ||= args[2];
    return { rows: bound ? [{ address: bound }] : [] };
  } };
  const message = bitcoinBindingMessage(walletId, accountId, base.descriptor.address, descriptor.address);
  const body = { address: descriptor.address, signature: await withBitcoinAccount(seed, noNetwork, account => account.sign(message)), ownerSignature: await base.signMessage(message) };
  try {
    assert.equal((await registerBitcoin(db, { wallet_id: walletId }, accountId, body)).address, descriptor.address);
    await assert.rejects(registerBitcoin(db, { wallet_id: randomUUID() }, accountId, body), { code: 'INVALID_REQUEST' });
    const foreign = { ...body, address: await withBitcoinAccount(new Uint8Array(64).fill(2), noNetwork, account => account.getAddress()) };
    await assert.rejects(registerBitcoin(db, { wallet_id: walletId }, accountId, foreign), { code: 'INVALID_REQUEST' });
  } finally { base.dispose(); }
});
test('swap advertises only Midori Bitcoin and registered Base assets', async () => {
  const walletId = randomUUID(), accountId = randomUUID();
  const usdc = '0x833589fcd6edb6e08f4c7c32d4f71b54bda02913';
  const db = { async query(sql) {
    if (sql.startsWith('SELECT id,descriptor')) return { rows: [{ id: accountId, descriptor: { address: '0x' + '12'.repeat(20) } }] };
    if (sql.includes('FROM account_assets')) return { rows: [{ contract: usdc, symbol: 'USDC', decimals: 6 }] };
    if (sql.includes('FROM bitcoin_accounts')) return { rows: [{ address: descriptor.address }] };
    if (sql.includes('FROM spark_accounts')) return { rows: [] };
    throw Error('Unexpected database query');
  } };
  const btc = { ticker: 'btc', name: 'Bitcoin', network: 'bitcoin', contract: null, decimals: 8, sell: true, buy: true };
  const provider = { currencies: async () => [btc, { ...btc, ticker: 'wbtc', network: 'ethereum' }, { ...btc, ticker: 'test-btc', network: 'testnet' }, { ticker: 'usdcbase', network: 'base', contract: usdc, decimals: 6, buy: true, sell: true }] };
  const options = await exchangeService(db, { changellyEnabled: true }, provider).options(walletId, accountId);
  assert.deepEqual(options.providers[0].destinations.map(asset => asset.ticker), ['btc', 'usdcbase']);
  assert.equal(options.providers[0].destinations[0].address, descriptor.address);
});

test('portfolio reference values convert eight-decimal BTC before adding stablecoins', async () => {
  const { portfolioReferenceValue } = await import('../../web/src/lib/account/portfolio.js');
  const { Amount } = await import('../../web/src/lib/account/domain.js');
  const assets = [{ crypto: { symbol: 'USDC' }, balance: new Amount('2500000', 6) }, { crypto: { symbol: 'BTC' }, balance: new Amount('12345678', 8), market: { usdPrice: 60000, price: 240000000, currency: 'COP' } }];
  assert.equal(portfolioReferenceValue(assets), '7409.91');
  assert.equal(portfolioReferenceValue([{ ...assets[1], market: undefined }]), null);
  assert.equal(portfolioReferenceValue([{ ...assets[1], balance: new Amount('0', 8) }]), '0.00');
});

test('Bitcoin history preserves native amounts, direction, fees and explorer links', async () => {
  const { default: BitcoinWallet } = await import('../../web/src/lib/account/BitcoinWallet.js');
  const crypto = { symbol: 'BTC', decimals: 8, platform: 'bitcoin' };
  const wallet = new BitcoinWallet({ crypto, platform: crypto, session: { account: { descriptor: { derivationPath: 'base' } } } }, { address: descriptor.address, derivationPath: descriptor.path });
  const incoming = wallet.transaction({ txid: 'a'.repeat(64), vin: [{ prevout: { scriptpubkey_address: 'foreign', value: 20000 } }], vout: [{ scriptpubkey_address: descriptor.address, value: 12345 }], fee: 500, status: { confirmed: true, block_time: 1795080000 } });
  assert.equal(incoming.amount.toString(), '0.00012345'); assert.equal(incoming.incoming, true); assert.equal(incoming.network, 'Bitcoin');
  const outgoing = wallet.transaction({ txid: 'b'.repeat(64), vin: [{ prevout: { scriptpubkey_address: descriptor.address, value: 10000 } }], vout: [{ scriptpubkey_address: descriptor.address, value: 1000 }, { scriptpubkey_address: 'foreign', value: 8500 }], fee: 500, status: { confirmed: false } });
  assert.equal(outgoing.amount.toString(), '0.000085'); assert.equal(outgoing.networkFee.toString(), '0.000005'); assert.equal(outgoing.incoming, false); assert.equal(outgoing.chainStatus, 'pending');
  assert.equal(outgoing.url, `https://mempool.space/tx/${'b'.repeat(64)}`);
});

test('Bitcoin exchange deposit is bound before broadcast and cannot be paid twice', async () => {
  const transaction = new Transaction(); transaction.addInput(new Uint8Array(32), 0xffffffff); transaction.addOutput(btcAddress.toOutputScript(descriptor.address, networks.bitcoin), 10000n);
  const row = { id: randomUUID(), status: 'waiting', payin_address: descriptor.address, quote: { from: { ticker: 'btc' }, amountFrom: '0.0001' }, bitcoin_deposit_hash: null };
  const db = { async transaction(action) { return action({ async query(sql, args) { if (sql.startsWith('UPDATE exchange_orders')) row.bitcoin_deposit_hash = args[1]; return { rows: [row] }; } }); } };
  let broadcasts = 0;
  const gateway = async (_, raw) => { assert.equal(row.bitcoin_deposit_hash, bitcoinTransactionId(raw)); broadcasts++; return bitcoinTransactionId(raw); };
  const body = { raw: transaction.toHex(), exchangeOrderId: row.id };
  assert.equal(await broadcastBitcoin(db, { wallet_id: randomUUID() }, body, gateway), transaction.getId());
  transaction.locktime = 1;
  await assert.rejects(broadcastBitcoin(db, { wallet_id: randomUUID() }, { ...body, raw: transaction.toHex() }, gateway), { code: 'INVALID_REQUEST' });
  assert.equal(broadcasts, 1);
});
