import { randomUUID } from 'node:crypto';
import { getAddress, parseUnits, formatUnits } from 'ethers';
import { ApiError, exactKeys, notFound, requireValue } from './errors.js';
import { uuidPattern } from './crypto.js';
import { account } from './repositories.js';
import { changellyClient, exchangeDecimal } from './changelly.js';

const fail = (code, message, status = 409) => new ApiError(status, code, message);
const terminal = ['finished','failed','refunded','expired','overdue'];
const sameAddress = (a, b) => typeof a === 'string' && typeof b === 'string' && (/^0x[0-9a-fA-F]{40}$/.test(a) && /^0x[0-9a-fA-F]{40}$/.test(b) ? a.toLowerCase() === b.toLowerCase() : a === b);
const states = ['waiting','confirming','exchanging','sending','finished','failed','refunded','hold','expired','overdue'];
const baseAsset = (currency, assets) => /^base$/i.test(currency.network) && currency.decimals === 6 && assets.find(asset => asset.contract.toLowerCase() === currency.contract);
function present(row) {
  return { id: row.id, provider: 'changelly', status: row.status, quote: row.quote, payoutAddress: row.payout_address,
    payoutExtraId: row.payout_extra_id, paymentIntentId: row.payment_intent_id || null, depositAddress: row.payin_address, providerId: row.provider_id,
    amountTo: row.result?.amountTo ?? null, payoutHash: row.result?.payoutHash ?? null, createdAt: row.created_at, updatedAt: row.updated_at };
}
const selected = 'SELECT o.*,q.quote FROM exchange_orders o JOIN exchange_quotes q ON q.id=o.quote_id';
export function exchangeService(db, config, provider = changellyClient(config)) {
  const enabled = () => { if (!config.changellyEnabled) throw fail('EXCHANGE_UNAVAILABLE', 'Crypto conversion is unavailable', 503); };
  const load = async (walletId, id) => {
    requireValue(uuidPattern.test(id), 'Invalid exchange ID');
    const { rows } = await db.query(`${selected} WHERE o.id=$1 AND o.wallet_id=$2`, [id, walletId]);
    if (!rows[0]) throw notFound(); return rows[0];
  };
  const refresh = async row => {
    if (!row.provider_id) return row;
    const data = await provider.transaction(row.provider_id);
    const q = row.quote;
    if (!states.includes(data.status) || data.currencyFrom !== q.from.ticker || data.currencyTo !== q.to.ticker
      || !sameAddress(data.payoutAddress, row.payout_address) || data.payinAddress?.toLowerCase() !== row.payin_address.toLowerCase()) throw fail('EXCHANGE_PROVIDER_UNAVAILABLE', 'The exchange details could not be verified', 502);
    if (terminal.includes(row.status) && data.status !== 'refunded' || row.status === 'refunded') return row;
    const amount = exchangeDecimal(data.amountTo) ? data.amountTo : null;
    const result = { amountTo: amount, payoutHash: typeof data.payoutHash === 'string' && /^[a-zA-Z0-9]{1,160}$/.test(data.payoutHash) ? data.payoutHash : null };
    const { rows } = await db.query("UPDATE exchange_orders SET status=$2,result=$3,updated_at=clock_timestamp() WHERE id=$1 AND (status NOT IN ('finished','failed','refunded','expired','overdue') OR $2='refunded') RETURNING *", [row.id, data.status, result]);
    return { ...(rows[0] || row), quote: q };
  };
  return {
    async options(walletId, accountId) {
      enabled(); const saved = await account(db, walletId, accountId);
      const currencies = await provider.currencies();
      const sources = currencies.filter(item => item.sell && baseAsset(item, saved.assets)).map(item => ({ ...item, symbol: baseAsset(item, saved.assets).symbol }));
      const destinations = currencies.filter(item => item.buy && item.network && Number.isInteger(item.decimals) && item.decimals >= 0 && item.decimals <= 18);
      return { providers: [{ id: 'changelly', name: 'Changelly', sources, destinations }] };
    },
    async quote(walletId, accountId, body) {
      enabled(); exactKeys(body, ['from','to','amount']);
      requireValue(typeof body.from === 'string' && typeof body.to === 'string' && body.from !== body.to, 'Invalid currency pair');
      requireValue(exchangeDecimal(body.amount) && parseUnits(body.amount, 36) > 0n, 'Invalid amount');
      const options = await this.options(walletId, accountId), { sources, destinations } = options.providers[0];
      const from = sources.find(item => item.ticker === body.from), to = destinations.find(item => item.ticker === body.to);
      requireValue(from && to, 'Unsupported currency or network');
      let atomic; try { atomic = parseUnits(body.amount, from.decimals); } catch { throw fail('INVALID_REQUEST', 'Invalid amount precision', 400); }
      const amountFrom = formatUnits(atomic, from.decimals);
      const estimated = await provider.estimate(from.ticker, to.ticker, amountFrom);
      if (estimated.min && parseUnits(amountFrom, 36) < parseUnits(estimated.min, 36) || estimated.max && parseUnits(amountFrom, 36) > parseUnits(estimated.max, 36)) throw fail('EXCHANGE_PAIR_UNAVAILABLE', 'This currency pair or amount is unavailable');
      const quote = { provider: 'changelly', from, to, amountFrom, ...estimated };
      const id = randomUUID();
      const { rows } = await db.query('INSERT INTO exchange_quotes(id,wallet_id,account_id,quote) VALUES($1,$2,$3,$4) RETURNING expires_at', [id, walletId, accountId, quote]);
      return { id, ...quote, expiresAt: rows[0].expires_at };
    },
    async create(principal, accountId, body) {
      enabled(); if (!config.paymentsEnabled) throw fail('PAYMENTS_UNAVAILABLE', 'Sending is temporarily unavailable', 503);
      exactKeys(body, ['quoteId','idempotencyKey','payoutAddress'], ['payoutExtraId']);
      requireValue(uuidPattern.test(body.quoteId) && uuidPattern.test(body.idempotencyKey), 'Invalid exchange ID');
      requireValue(typeof body.payoutAddress === 'string' && body.payoutAddress.length >= 3 && body.payoutAddress.length <= 256 && !/[\s\x00-\x1f]/.test(body.payoutAddress), 'Invalid payout address');
      requireValue(body.payoutExtraId === undefined || typeof body.payoutExtraId === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(body.payoutExtraId), 'Invalid destination tag or memo');
      const saved = await account(db, principal.wallet_id, accountId);
      const previous = await db.query(`${selected} WHERE o.wallet_id=$1 AND o.idempotency_key=$2`, [principal.wallet_id, body.idempotencyKey]);
      if (previous.rows[0]) {
        const row = previous.rows[0];
        if (row.account_id !== accountId || row.quote_id !== body.quoteId || row.payout_address !== body.payoutAddress || row.payout_extra_id !== (body.payoutExtraId ?? null)) throw fail('IDEMPOTENCY_CONFLICT', 'This exchange key was already used');
        return present(row);
      }
      const { rows: quotes } = await db.query('SELECT quote FROM exchange_quotes WHERE id=$1 AND wallet_id=$2 AND account_id=$3 AND expires_at>clock_timestamp()', [body.quoteId, principal.wallet_id, accountId]);
      if (!quotes[0]) throw fail('EXCHANGE_QUOTE_EXPIRED', 'Review the exchange again');
      const quote = quotes[0].quote;
      requireValue(!quote.to.extraIdName || body.payoutExtraId, 'A destination tag or memo is required');
      requireValue(await provider.validate(quote.to.ticker, body.payoutAddress, body.payoutExtraId), 'Invalid payout address');
      const unresolved = await db.query("SELECT id FROM exchange_orders WHERE wallet_id=$1 AND account_id=$2 AND status IN ('creating','uncertain') LIMIT 1", [principal.wallet_id, accountId]);
      if (unresolved.rows.length) throw fail('IDEMPOTENCY_CONFLICT', 'Review the unconfirmed exchange before starting another');
      let row;
      try {
        const result = await db.query('INSERT INTO exchange_orders(id,wallet_id,account_id,idempotency_key,quote_id,payout_address,payout_extra_id) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING *', [randomUUID(), principal.wallet_id, accountId, body.idempotencyKey, body.quoteId, body.payoutAddress, body.payoutExtraId ?? null]);
        row = result.rows[0];
      } catch (error) { if (error.code === '23505') throw fail('IDEMPOTENCY_CONFLICT', 'Review the existing exchange'); throw error; }
      try {
        const data = await provider.create({ from: quote.from.ticker, to: quote.to.ticker, amountFrom: quote.amountFrom, address: body.payoutAddress, refundAddress: getAddress(saved.address), ...(body.payoutExtraId ? { extraId: body.payoutExtraId } : {}) });
        requireValue(typeof data.id === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(data.id) && data.currencyFrom === quote.from.ticker && data.currencyTo === quote.to.ticker && sameAddress(data.payoutAddress, body.payoutAddress), 'The exchange details could not be verified');
        const deposit = getAddress(data.payinAddress);
        requireValue(!data.payinExtraId && deposit.toLowerCase() !== saved.address.toLowerCase(), 'Unsupported deposit address');
        const { rows } = await db.query("UPDATE exchange_orders SET status='waiting',provider_id=$2,payin_address=$3,updated_at=clock_timestamp() WHERE id=$1 RETURNING *", [row.id, data.id, deposit]);
        return present({ ...rows[0], quote });
      } catch {
        await db.query("UPDATE exchange_orders SET status='uncertain',updated_at=clock_timestamp() WHERE id=$1", [row.id]);
        return present({ ...row, status: 'uncertain', quote });
      }
    },
    async list(walletId, accountId) {
      await account(db, walletId, accountId);
      const { rows } = await db.query(`${selected} WHERE o.wallet_id=$1 AND o.account_id=$2 ORDER BY o.created_at DESC LIMIT 50`, [walletId, accountId]);
      return { orders: rows.map(present) };
    },
    async get(walletId, id) {
      const row = await load(walletId, id);
      return present(config.changellyEnabled ? await refresh(row) : row);
    },
  };
}
