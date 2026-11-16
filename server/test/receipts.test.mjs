import test from 'node:test';
import assert from 'node:assert/strict';
import { receiptDocument, receiptService } from '../lib/receipts.js';
const owner = '11111111-1111-4111-8111-111111111111', accountId = '22222222-2222-4222-8222-222222222222', orderId = '33333333-3333-4333-8333-333333333333';
const sender = '0x1111111111111111111111111111111111111111', recipient = '0x2222222222222222222222222222222222222222';
const tagDb = { query: async (sql, args) => ({ rows: [{ alias: args[1] === sender ? 'sender' : 'receiver' }] }) };
test('receipt preserves exact transfers, authoritative parties and known costs', async () => {
  const document = await receiptDocument(tagDb, 'payment', { id: 'e1', status: 'confirmed', chainId: 8453, symbol: 'USDC', decimals: 6, amountAtomic: '9007199254740993123456', incoming: false, from: sender, to: recipient, timestamp: '2026-11-15T18:00:00Z', origin: 'midori', midoriFeeAtomic: '0', actualFeeAtomic: '125000', feeMode: 'token' }, { address: sender });
  assert.equal(document.amount.value, '9007199254740993.123456'); assert.equal(document.from.tag, '$sender'); assert.equal(document.to.tag, '$receiver'); assert.equal(document.to.address.toLowerCase(), recipient); assert.equal(document.networkCost.value, '0.125'); assert.equal(document.date, '2026-11-15T18:00:00.000Z');
});
function fixture() {
  let stored;
  const order = { id: orderId, account_id: accountId, wallet_id: owner, wallet_address: sender, kind: 'sell', status: 'created', environment: 'STAGING', provider: 'transak', fiat_currency: 'USD', crypto_currency: 'USDC', fiat_amount: null, crypto_amount: '20.000000000000000000', created_at: '2026-11-15T18:00:00Z', updated_at: '2026-11-15T18:00:00Z' };
  const db = { async query(sql, args) {
    if (sql.startsWith('SELECT id,descriptor FROM chain_accounts')) return { rows: args[0] === accountId && args[1] === owner ? [{ id: accountId, descriptor: { address: sender } }] : [] };
    if (sql.includes('FROM account_assets')) return { rows: [] };
    if (sql.includes('auth_attempts')) return { rows: [{ attempts: 1 }] };
    if (sql.includes('FROM wallet_tags')) return { rows: [{ alias: 'sender' }] };
    if (sql.includes('FROM fiat_orders')) return { rows: args[0] === orderId && args[1] === owner && args[2] === accountId ? [order] : [] };
    if (sql.startsWith('INSERT INTO receipt_shares')) { stored = { wallet_id: args[1], account_id: args[2], source: args[3], reference: args[4], token_hash: args[5], payload: structuredClone(args[6]), payload_hash: args[7] }; return { rows: [] }; }
    if (sql.includes('FROM receipt_shares')) return { rows: stored?.token_hash === args[0] ? [stored] : [] };
    throw Error('Unexpected fixture query');
  } };
  return { db, order, stored: () => stored };
}
test('public QR resolves an immutable server snapshot while current status can change', async () => {
  const f = fixture(), service = receiptService({ publicOrigin: 'https://wallet.example', receiptPath: '/wallet/receipt' });
  const issued = await service.issue(f.db, { wallet_id: owner }, accountId, { source: 'ramp', reference: orderId });
  const link = new URL(issued.verificationUrl), token = link.hash.slice(1); assert.equal(link.pathname, '/wallet/receipt'); assert.equal(link.search, ''); assert.equal(token.length, 43); assert.notEqual(f.stored().token_hash, token);
  assert.equal(issued.document.amount.value, '20'); assert.equal(issued.document.environment, 'STAGING'); assert.equal(issued.document.to, null); assert.equal(issued.document.from.tag, '$sender');
  f.order.status = 'completed'; const verified = await service.verify(f.db, { token }); assert.equal(verified.document.status, 'created'); assert.equal(verified.currentStatus, 'completed');
  await assert.rejects(service.verify(f.db, { token: 'A'.repeat(43) }), error => error.status === 404);
  f.stored().payload.amount.value = '2000'; await assert.rejects(service.verify(f.db, { token }), error => error.status === 404);
});
test('issuance rejects client-supplied financial data, other accounts and missing records', async () => {
  const f = fixture(), service = receiptService({ publicOrigin: 'https://wallet.example' });
  await assert.rejects(service.issue(f.db, { wallet_id: owner }, accountId, { source: 'ramp', reference: orderId, amount: '2000', status: 'completed' }), error => error.status === 400);
  await assert.rejects(service.issue(f.db, { wallet_id: 'foreign' }, accountId, { source: 'ramp', reference: orderId }), error => error.status === 404);
  await assert.rejects(service.issue(f.db, { wallet_id: owner }, accountId, { source: 'ramp', reference: accountId }), error => error.status === 404);
});
