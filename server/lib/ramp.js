import { randomUUID } from 'node:crypto';
import { isIP } from 'node:net';
import { getAddress } from 'ethers';
import { ApiError, exactKeys, requireValue, notFound } from './errors.js';
import { uuidPattern } from './crypto.js';
import { account } from './repositories.js';
import { transakClient } from './transak.js';

const fail = (code, message, status = 409) => new ApiError(status, code, message);
const terminal = ['completed', 'failed', 'refunded', 'expired'];
const decimal = (value, max) => typeof value === 'string' && /^[0-9]{1,18}(\.[0-9]{1,18})?$/.test(value) && value.length <= max && Number(value) > 0;
// Numeric comparison, not string equality: Postgres round-trips "100" as
// numeric(18,2) text "100.00", which must still match the idempotency key.
const sameAmount = (stored, requested) => (stored == null && requested === undefined) || (stored != null && requested !== undefined && Number(stored) === Number(requested));

// Transak's own order status vocabulary (see docs.transak.com/features/
// webhooks) collapsed onto the narrower lifecycle this app persists.
const STATUS = {
  AWAITING_PAYMENT_FROM_USER: 'awaiting_payment', PAYMENT_DONE_MARKED_BY_USER: 'awaiting_payment',
  PROCESSING: 'processing', PENDING_DELIVERY_FROM_TRANSAK: 'processing', ON_HOLD_PENDING_DELIVERY_FROM_TRANSAK: 'processing',
  COMPLETED: 'completed', CANCELLED: 'failed', FAILED: 'failed', EXPIRED: 'expired', REFUNDED: 'refunded',
};
const mapStatus = value => STATUS[value] || 'processing';

export function publicOrder(row) {
  return {
    id: row.id, accountId: row.account_id, provider: row.provider, kind: row.kind, status: row.status,
    fiatCurrency: row.fiat_currency, cryptoCurrency: row.crypto_currency, network: row.network, walletAddress: getAddress(row.wallet_address),
    fiatAmount: row.fiat_amount == null ? null : String(row.fiat_amount), cryptoAmount: row.crypto_amount == null ? null : String(row.crypto_amount),
    paymentMethod: row.payment_method, transactionHash: row.transaction_hash, failureReason: row.failure_reason,
    createdAt: row.created_at, updatedAt: row.updated_at,
  };
}

export function rampService(db, config, client = transakClient(config)) {
  const enabled = () => { if (!config.transakEnabled) throw fail('RAMP_UNAVAILABLE', 'Buying or selling crypto is temporarily unavailable', 503); };
  // Applies a verified webhook payload (see lib/transak.js verifyWebhook) or
  // a reconciled order from reconcile() below. `claims` is the decoded JWT
  // body shape: { webhookData, eventID, createdAt }.
  const applyWebhook = async claims => {
    const data = claims?.webhookData;
    requireValue(data && typeof data === 'object' && typeof claims.eventID === 'string', 'Malformed webhook payload');
    if (!uuidPattern.test(data.partnerOrderId || '')) return { applied: false };
    return db.transaction(async client_ => {
      const result = await client_.query('SELECT * FROM fiat_orders WHERE id=$1 FOR UPDATE', [data.partnerOrderId]);
      if (!result.rows.length) return { applied: false };
      const row = result.rows[0];
      if (terminal.includes(row.status)) return { applied: true, order: row };
      const status = mapStatus(data.status);
      const updated = await client_.query(`UPDATE fiat_orders SET status=$2,provider_order_id=COALESCE($3,provider_order_id),transaction_hash=COALESCE($4,transaction_hash),
        failure_reason=$5,raw=$6,updated_at=clock_timestamp() WHERE id=$1 RETURNING *`,
        [row.id, status, data.id || null, data.transactionHash || null, status === 'failed' ? (data.status || 'unknown') : null, data]);
      return { applied: true, order: updated.rows[0] };
    });
  };
  return {
    async create(principal, accountId, body, userIp) {
      enabled();
      exactKeys(body, ['idempotencyKey', 'kind', 'cryptoCurrency', 'fiatCurrency'], ['fiatAmount', 'cryptoAmount', 'paymentMethod']);
      requireValue(uuidPattern.test(accountId) && uuidPattern.test(body.idempotencyKey), 'Invalid order ID');
      requireValue(['buy', 'sell'].includes(body.kind), 'Invalid order kind');
      requireValue(['USDC', 'USDT'].includes(body.cryptoCurrency), 'Unsupported crypto currency');
      requireValue(/^[A-Z]{3}$/.test(body.fiatCurrency), 'Invalid fiat currency');
      if (body.fiatAmount !== undefined) requireValue(decimal(body.fiatAmount, 18), 'Invalid fiat amount');
      if (body.cryptoAmount !== undefined) requireValue(decimal(body.cryptoAmount, 36), 'Invalid crypto amount');
      requireValue(body.kind === 'buy' || body.cryptoAmount !== undefined, 'Selling requires a crypto amount');
      requireValue(body.paymentMethod === undefined || (typeof body.paymentMethod === 'string' && /^[a-z0-9_]{1,64}$/.test(body.paymentMethod)), 'Invalid payment method');
      requireValue(typeof userIp === 'string' && isIP(userIp) !== 0, 'Unable to determine the request origin');
      const saved = await account(db, principal.wallet_id, accountId);
      const row = await db.transaction(async client_ => {
        const previous = await client_.query('SELECT *,fiat_amount::text,crypto_amount::text FROM fiat_orders WHERE wallet_id=$1 AND idempotency_key=$2', [principal.wallet_id, body.idempotencyKey]);
        if (previous.rows.length) {
          const existing = previous.rows[0];
          if (!(existing.account_id === accountId && existing.kind === body.kind && existing.crypto_currency === body.cryptoCurrency && existing.fiat_currency === body.fiatCurrency
            && sameAmount(existing.fiat_amount, body.fiatAmount) && sameAmount(existing.crypto_amount, body.cryptoAmount))) throw fail('IDEMPOTENCY_CONFLICT', 'This order key was already used with different parameters');
          return existing;
        }
        const id = randomUUID();
        const inserted = await client_.query(`INSERT INTO fiat_orders(id,wallet_id,account_id,idempotency_key,kind,fiat_currency,crypto_currency,wallet_address,fiat_amount,crypto_amount,payment_method)
          VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING *,fiat_amount::text,crypto_amount::text`,
          [id, principal.wallet_id, accountId, body.idempotencyKey, body.kind, body.fiatCurrency, body.cryptoCurrency, saved.address.toLowerCase(), body.fiatAmount ?? null, body.cryptoAmount ?? null, body.paymentMethod ?? null]);
        return inserted.rows[0];
      });
      const widgetParams = {
        apiKey: config.transakApiKey, referrerDomain: config.transakReferrerDomain, partnerOrderId: row.id,
        productsAvailed: row.kind.toUpperCase(), fiatCurrency: row.fiat_currency, cryptoCurrencyCode: row.crypto_currency, network: row.network,
        ...(row.kind === 'buy' ? { walletAddress: getAddress(row.wallet_address), disableWalletAddressForm: true } : {}),
        ...(row.fiat_amount != null ? { fiatAmount: Number(row.fiat_amount) } : {}),
        ...(row.crypto_amount != null ? { cryptoAmount: Number(row.crypto_amount) } : {}),
        ...(row.payment_method ? { paymentMethod: row.payment_method } : {}),
      };
      const widgetUrl = await client.widgetSession(widgetParams, userIp);
      return { ...publicOrder(row), widgetUrl };
    },
    async list(walletId, accountId) {
      await account(db, walletId, accountId);
      const { rows } = await db.query('SELECT *,fiat_amount::text,crypto_amount::text FROM fiat_orders WHERE wallet_id=$1 AND account_id=$2 ORDER BY created_at DESC LIMIT 50', [walletId, accountId]);
      return { orders: rows.map(publicOrder) };
    },
    async get(walletId, id) {
      requireValue(uuidPattern.test(id), 'Invalid order ID');
      const { rows } = await db.query('SELECT *,fiat_amount::text,crypto_amount::text FROM fiat_orders WHERE id=$1 AND wallet_id=$2', [id, walletId]);
      if (!rows.length) throw notFound();
      return publicOrder(rows[0]);
    },
    applyWebhook,
    // Verifies the raw `data` field from a Transak webhook POST body and, if
    // valid, applies it.
    async handleWebhook(token) {
      enabled();
      const claims = await client.verifyWebhook(token);
      if (!claims) throw fail('RAMP_WEBHOOK_SIGNATURE_INVALID', 'Webhook signature could not be verified', 401);
      return applyWebhook(claims);
    },
    // Polling fallback: Transak webhooks require manual KYB approval before
    // they are ever called (docs.transak.com/features/webhooks), so a freshly
    // configured integration — or a dropped delivery — relies on this to make
    // progress. Looks orders up by the partnerOrderId we minted at create().
    async reconcile(limit = 20) {
      const { rows } = await db.query(`SELECT id FROM fiat_orders WHERE status NOT IN ('completed','failed','refunded','expired') AND updated_at<=clock_timestamp()-interval '20 seconds' ORDER BY updated_at LIMIT $1`, [limit]);
      let updated = 0;
      for (const row of rows) {
        let order;
        try { order = await client.findOrderByPartnerOrderId(row.id); } catch { continue; }
        if (!order || typeof order.status !== 'string') continue;
        const applied = await applyWebhook({ eventID: 'RECONCILED', webhookData: { ...order, partnerOrderId: row.id } });
        if (applied.applied) updated++;
      }
      return updated;
    },
  };
}
