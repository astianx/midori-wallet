import { bitcoinAddress } from './bitcoin.js';
import { randomUUID } from 'node:crypto';
import { isIP } from 'node:net';
import { getAddress, parseUnits } from 'ethers';
import { ApiError, exactKeys, requireValue, notFound } from './errors.js';
import { uuidPattern } from './crypto.js';
import { account } from './repositories.js';
import { transakClient } from './transak.js';

const fail = (code, message, status = 409) => new ApiError(status, code, message);
const terminal = ['completed', 'failed', 'refunded', 'expired'];
const decimal = (value, max) => typeof value === 'string' && /^[0-9]{1,16}(\.[0-9]{1,8})?$/.test(value) && value.length <= max && Number(value) > 0;
// Numeric comparison, not string equality: Postgres round-trips "100" as
// numeric text "100.0000", which must still match the idempotency key.
const sameAmount = (stored, requested) => (stored == null && requested === undefined) || (stored != null && requested !== undefined && parseUnits(String(stored), 18) === parseUnits(requested, 18));

// Transak's own order status vocabulary (see docs.transak.com/features/
// webhooks) collapsed onto the narrower lifecycle this app persists.
const STATUS = {
  AWAITING_PAYMENT_FROM_USER: 'awaiting_payment', PAYMENT_DONE_MARKED_BY_USER: 'awaiting_payment',
  PROCESSING: 'processing', PENDING_DELIVERY_FROM_TRANSAK: 'processing', ON_HOLD_PENDING_DELIVERY_FROM_TRANSAK: 'processing',
  PENDING_DELIVERY_FROM_USER: 'awaiting_payment', AWAITING_CRYPTO_FROM_USER: 'awaiting_payment',
  COMPLETED: 'completed', CANCELLED: 'failed', FAILED: 'failed', EXPIRED: 'expired', REFUNDED: 'refunded',
};
const mapStatus = value => Object.hasOwn(STATUS, value) ? STATUS[value] : undefined;

export function publicOrder(row) {
  return {
    id: row.id, environment: row.environment, accountId: row.account_id, provider: row.provider, kind: row.kind, status: row.status,
    fiatCurrency: row.fiat_currency, cryptoCurrency: row.crypto_currency, network: row.network, walletAddress: row.network === 'bitcoin' ? row.wallet_address : getAddress(row.wallet_address),
    fiatAmount: row.fiat_amount == null ? null : String(row.fiat_amount), cryptoAmount: row.crypto_amount == null ? null : String(row.crypto_amount),
    paymentMethod: row.payment_method, transactionHash: row.transaction_hash, failureReason: row.failure_reason,
    createdAt: row.created_at, updatedAt: row.updated_at,
  };
}

export function rampService(db, config, client = transakClient(config, fetch, db)) {
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
      if (row.environment !== config.transakEnvironment) return { applied: false };
      const status = mapStatus(data.status);
      if (!status || (data.isBuyOrSell && data.isBuyOrSell !== row.kind.toUpperCase()) || (data.network && data.network !== (row.network === 'bitcoin' ? 'mainnet' : row.network))
        || (data.cryptoCurrency && data.cryptoCurrency !== row.crypto_currency) || (data.fiatCurrency && data.fiatCurrency !== row.fiat_currency)
        || (row.kind === 'buy' && data.walletAddress && data.walletAddress.toLowerCase() !== row.wallet_address.toLowerCase())
        || (row.provider_order_id && (data.id || data._id) && row.provider_order_id !== (data.id || data._id))) return { applied: false };
      if (terminal.includes(row.status) && status !== 'refunded') return { applied: true, order: row };
      if (row.status === 'refunded' || (row.status === 'processing' && status === 'awaiting_payment')) return { applied: true, order: row };
      const raw = { id: data.id || data._id, partnerOrderId: data.partnerOrderId, status: data.status, updatedAt: data.updatedAt, transactionHash: data.transactionHash };
      const updated = await client_.query(`UPDATE fiat_orders SET status=$2,provider_order_id=COALESCE($3,provider_order_id),transaction_hash=COALESCE($4,transaction_hash),
        failure_reason=$5,raw=$6,updated_at=clock_timestamp() WHERE id=$1 RETURNING *`,
        [row.id, status, data.id || data._id || null, data.transactionHash || null, status === 'failed' ? data.status : null, raw]);
      return { applied: true, order: updated.rows[0] };
    });
  };
  return {
    async options() { enabled(); return client.options(); },
    async create(principal, accountId, body, userIp) {
      enabled();
      exactKeys(body, ['idempotencyKey', 'kind', 'cryptoCurrency', 'fiatCurrency'], ['fiatAmount', 'cryptoAmount', 'paymentMethod']);
      requireValue(uuidPattern.test(accountId) && uuidPattern.test(body.idempotencyKey), 'Invalid order ID');
      requireValue(['buy', 'sell'].includes(body.kind), 'Invalid order kind');
      requireValue(['USDC', 'USDT', 'BTC'].includes(body.cryptoCurrency), 'Unsupported crypto currency');
      requireValue(/^[A-Z]{3}$/.test(body.fiatCurrency), 'Invalid fiat currency');
      requireValue((body.fiatAmount !== undefined) !== (body.cryptoAmount !== undefined), 'Provide exactly one amount');
      if (body.fiatAmount !== undefined) requireValue(decimal(body.fiatAmount, 18), 'Invalid fiat amount');
      if (body.cryptoAmount !== undefined) requireValue(decimal(body.cryptoAmount, 36), 'Invalid crypto amount');
      requireValue(body.kind === 'buy' || body.cryptoAmount !== undefined, 'Selling requires a crypto amount');
      requireValue(body.paymentMethod === undefined || (typeof body.paymentMethod === 'string' && /^[a-z0-9_]{1,64}$/.test(body.paymentMethod)), 'Invalid payment method');
      requireValue(typeof userIp === 'string' && isIP(userIp) !== 0, 'Unable to determine the request origin');
      const saved = await account(db, principal.wallet_id, accountId);
      const options = await client.options();
      const asset = options.assets.find(item => item.symbol === body.cryptoCurrency && item[body.kind]);
      const fiat = options.fiatCurrencies.find(item => item.code === body.fiatCurrency && item[body.kind]);
      requireValue(asset && fiat, 'This buy/sell pair is unavailable');
      if (body.cryptoAmount !== undefined) {
        try { parseUnits(body.cryptoAmount, Math.min(body.cryptoCurrency === 'BTC' ? 8 : 6, asset.roundOff ?? 6)); } catch { throw fail('INVALID_REQUEST', 'Invalid crypto precision', 400); }
      }
      if (body.fiatAmount !== undefined) {
        try { parseUnits(body.fiatAmount, fiat.decimals); } catch { throw fail('INVALID_REQUEST', 'Invalid fiat precision', 400); }
      }
      const network = body.cryptoCurrency === 'BTC' ? 'bitcoin' : 'base';
      const address = network === 'bitcoin' ? await bitcoinAddress(db, principal.wallet_id, accountId) : saved.address.toLowerCase();
      requireValue(address && (!asset.network || asset.network === network), 'Unsupported wallet network');
      const row = await db.transaction(async client_ => {
        const previous = await client_.query('SELECT *,fiat_amount::text,crypto_amount::text FROM fiat_orders WHERE wallet_id=$1 AND idempotency_key=$2', [principal.wallet_id, body.idempotencyKey]);
        if (previous.rows.length) {
          const existing = previous.rows[0];
          if (!(existing.environment === config.transakEnvironment && existing.account_id === accountId && existing.kind === body.kind && existing.crypto_currency === body.cryptoCurrency && existing.fiat_currency === body.fiatCurrency
            && sameAmount(existing.fiat_amount, body.fiatAmount) && sameAmount(existing.crypto_amount, body.cryptoAmount) && existing.payment_method === (body.paymentMethod ?? null))) throw fail('IDEMPOTENCY_CONFLICT', 'This order key was already used with different parameters');
          return existing;
        }
        const id = randomUUID();
        const inserted = await client_.query(`INSERT INTO fiat_orders(id,wallet_id,account_id,idempotency_key,kind,fiat_currency,crypto_currency,wallet_address,fiat_amount,crypto_amount,payment_method,environment,network)
          VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13) ON CONFLICT(wallet_id,idempotency_key) DO NOTHING RETURNING *,fiat_amount::text,crypto_amount::text`,
          [id, principal.wallet_id, accountId, body.idempotencyKey, body.kind, body.fiatCurrency, body.cryptoCurrency, address, body.fiatAmount ?? null, body.cryptoAmount ?? null, body.paymentMethod ?? null, config.transakEnvironment, network]);
        if (inserted.rows.length) return inserted.rows[0];
        throw fail('IDEMPOTENCY_CONFLICT', 'This order key is already in use; check the order before trying again');
      });
      if (terminal.includes(row.status)) throw fail('IDEMPOTENCY_CONFLICT', 'This order has already finished');
      const widgetUrl = await client.createWidget({ ...row, wallet_address: row.network === 'bitcoin' ? row.wallet_address : getAddress(row.wallet_address) }, userIp);
      return { ...publicOrder(row), widgetUrl };
    },
    async list(walletId, accountId) {
      await account(db, walletId, accountId);
      const { rows } = await db.query('SELECT *,fiat_amount::text,crypto_amount::text FROM fiat_orders WHERE wallet_id=$1 AND account_id=$2 AND environment=$3 ORDER BY created_at DESC LIMIT 50', [walletId, accountId, config.transakEnvironment]);
      return { orders: rows.map(publicOrder) };
    },
    async get(walletId, id) {
      requireValue(uuidPattern.test(id), 'Invalid order ID');
      const { rows } = await db.query('SELECT *,fiat_amount::text,crypto_amount::text FROM fiat_orders WHERE id=$1 AND wallet_id=$2 AND environment=$3', [id, walletId, config.transakEnvironment]);
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
      enabled();
      const { rows } = await db.query(`SELECT id,kind FROM fiat_orders WHERE environment=$2 AND status NOT IN ('completed','failed','refunded','expired') AND updated_at<=clock_timestamp()-interval '20 seconds' ORDER BY updated_at LIMIT $1`, [limit, config.transakEnvironment]);
      let updated = 0;
      for (const row of rows) {
        let order;
        try { order = await client.findOrderByPartnerOrderId(row.id, row.kind); } catch { continue; }
        if (!order || typeof order.status !== 'string' || order.partnerOrderId !== row.id) continue;
        const applied = await applyWebhook({ eventID: 'RECONCILED', webhookData: order });
        if (applied.applied) updated++;
      }
      return updated;
    },
  };
}
