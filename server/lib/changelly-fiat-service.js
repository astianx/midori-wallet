import { randomUUID } from 'node:crypto';
import { parseUnits } from 'ethers';
import { account } from './repositories.js';
import { bitcoinAddress } from './bitcoin.js';
import { sparkAccount } from './spark.js';
import { ApiError, exactKeys, requireValue, notFound } from './errors.js';
import { uuidPattern } from './crypto.js';
import { changellyFiatClient, fiatCheckoutUrl } from './changelly-fiat.js';
import { exchangeDecimal } from './changelly.js';

const select = 'SELECT o.*,q.quote FROM changelly_fiat_orders o JOIN changelly_fiat_quotes q ON q.id=o.quote_id';
const states = { created: 'created', pending: 'processing', hold: 'processing', complete: 'completed', cancelled: 'failed', failed: 'failed', expired: 'expired', refunded: 'refunded' };
const terminal = ['completed','failed','expired','refunded'];
const positive = text => exchangeDecimal(text) && parseUnits(text, 36) > 0n;
const same = (a,b) => typeof a === 'string' && typeof b === 'string' && (/^0x/.test(a) ? a.toLowerCase() === b.toLowerCase() : a === b);
const flows = (item, kind) => (item.supportedFlows || item.supportedFlow || []).includes(kind);
const supportedProviders = new Set(['moonpay','paybis','topper']);
function present(row) {
  const q = row.quote, buy = q.kind === 'buy';
  return { id: row.id, provider: 'changelly', providerCode: q.providerCode, environment: row.environment, kind: q.kind, network: q.network, status: row.status, cryptoCurrency: q.cryptoCurrency, fiatCurrency: q.fiatCurrency, fiatAmount: buy ? q.amount : row.result?.payoutAmount || q.amountTo, cryptoAmount: buy ? row.result?.payoutAmount || q.amountTo : q.amount, walletAddress: q.walletAddress, paymentMethod: q.paymentMethod, checkoutUrl: row.checkout_url, createdAt: row.created_at, updatedAt: row.updated_at, providerOrderId: row.provider_id, quote: q };
}
export function changellyFiatService(db, config, provider = changellyFiatClient(config)) {
  let cache = new Map();
  const enabled = () => { if (!config.changellyFiatEnabled) throw new ApiError(503, 'RAMP_UNAVAILABLE', 'Changelly Fiat is unavailable'); };
  const catalog = async kind => {
    enabled(); if (cache.get(kind)?.until > Date.now()) return cache.get(kind).value;
    const [countries, currencies] = await Promise.all([provider.countries(kind), provider.currencies(kind)]);
    if (!Array.isArray(countries) || !Array.isArray(currencies)) throw new ApiError(502,'RAMP_PROVIDER_UNAVAILABLE','Invalid catalog');
    const value = { countries, currencies }; cache.set(kind, { until: Date.now() + 60000, value }); return value;
  };
  const wallet = async (walletId, accountId, symbol, network) => {
    const saved = await account(db, walletId, accountId);
    if (symbol === 'BTC' && network === 'bitcoin') return bitcoinAddress(db, walletId, accountId);
    if (symbol === 'BTC' && network === 'spark') return (await sparkAccount(db, walletId, accountId))?.deposit_address;
    if (network === 'base' && ['USDC','USDT'].includes(symbol) && saved.assets.some(item => item.symbol === symbol)) return saved.address;
  };
  // Fiat metadata has no token contract. Do not equate this wallet's bridged USDT with a provider's native USDT representation.
  const currency = (currencies, symbol, network) => symbol === 'USDT' ? undefined : currencies.find(item => item.type === 'crypto' && !item.extraIdName && (symbol === 'BTC' ? item.ticker === 'BTC' && /^bitcoin$/i.test(item.network) && Number(item.precision) === 8 : symbol === 'USDC' && /^base$/i.test(item.network) && Number(item.precision) === 6 && ['USDC','USDCBASE','USDC_BASE'].includes(item.ticker)) && ['bitcoin','spark','base'].includes(network));
  const load = async (walletId,id) => { requireValue(uuidPattern.test(id), 'Invalid order ID'); const row = (await db.query(`${select} WHERE o.wallet_id=$1 AND o.id=$2`, [walletId,id])).rows[0]; if (!row) throw notFound(); return row; };
  const refresh = async row => {
    if (!config.changellyFiatEnabled || terminal.includes(row.status)) return row;
    const data = await provider.orders(row.id), q = row.quote;
    if (!Array.isArray(data.orders) || data.orders.length > 1) throw new ApiError(502,'RAMP_PROVIDER_UNAVAILABLE','Invalid order response');
    if (!data.orders.length) return row; // A lost create response must never create a second order.
    const item = data.orders[0];
    requireValue(item.externalOrderId === row.id && item.externalUserId === row.wallet_id && item.type === q.kind && item.currencyFrom === q.currencyFrom && item.currencyTo === q.currencyTo && item.providerCode === q.providerCode && same(q.kind === 'buy' ? item.walletAddress : item.refundAddress, q.walletAddress) && positive(item.amountFrom) && parseUnits(item.amountFrom,36) === parseUnits(q.amount,36) && typeof item.orderId === 'string' && (!row.provider_id || row.provider_id === item.orderId), 'Order details changed');
    const checkout = fiatCheckoutUrl(item.redirectUrl, config.changellyFiatEnvironment); requireValue(checkout, 'Unsupported checkout');
    const status = states[item.status] || row.status, result = { payoutAmount: positive(item.payoutAmount) ? item.payoutAmount : null };
    const updated = await db.query('UPDATE changelly_fiat_orders SET status=$2,provider_id=$3,checkout_url=$4,result=$5,updated_at=clock_timestamp() WHERE id=$1 AND status NOT IN (\'completed\',\'failed\',\'expired\',\'refunded\') RETURNING *', [row.id,status,item.orderId,checkout,result]);
    return { ...(updated.rows[0] || row), quote: q };
  };
  return {
    async options(walletId, accountId, body) {
      exactKeys(body, ['kind','cryptoCurrency','network']); requireValue(['buy','sell'].includes(body.kind), 'Invalid flow');
      requireValue(await wallet(walletId,accountId,body.cryptoCurrency,body.network), 'Unsupported wallet');
      const data = await catalog(body.kind), asset = currency(data.currencies,body.cryptoCurrency,body.network);
      if (!asset) return { provider: 'changelly', environment: config.changellyFiatEnvironment, countries: [], currencies: [] };
      return { provider: 'changelly', environment: config.changellyFiatEnvironment, countries: data.countries.filter(item => item.providers?.some(p => supportedProviders.has(p.providerCode) && flows(p,body.kind))).map(({code,name,states}) => ({code,name,...(states ? {states} : {})})), currencies: data.currencies.filter(item => item.type === 'fiat' && /^[A-Z]{3}$/.test(item.ticker) && item.providers?.some(p => supportedProviders.has(p.providerCode) && flows(p,body.kind))).map(item => ({ code: item.ticker, decimals: 2 })) };
    },
    async quote(walletId,accountId,body,ip) {
      exactKeys(body,['kind','cryptoCurrency','network','fiatCurrency','country','amount'],['state']);
      requireValue(['buy','sell'].includes(body.kind) && positive(body.amount) && /^[A-Z]{2}$/.test(body.country) && /^[A-Z]{3}$/.test(body.fiatCurrency), 'Invalid order details');
      const address = await wallet(walletId,accountId,body.cryptoCurrency,body.network); requireValue(address, 'Unsupported wallet');
      const { countries,currencies } = await catalog(body.kind), asset = currency(currencies,body.cryptoCurrency,body.network), fiat = currencies.find(item => item.type === 'fiat' && item.ticker === body.fiatCurrency), country = countries.find(item => item.code === body.country);
      requireValue(asset && fiat && country && (body.country !== 'US' || country.states?.some(item => item.code === body.state)), 'Unsupported currency or country');
      const decimals = body.kind === 'buy' ? 2 : body.cryptoCurrency === 'BTC' ? 8 : 6;
      try { parseUnits(body.amount,decimals); } catch { throw new ApiError(400,'INVALID_REQUEST','Invalid amount precision'); }
      const currencyFrom = body.kind === 'buy' ? fiat.ticker : asset.ticker, currencyTo = body.kind === 'buy' ? asset.ticker : fiat.ticker;
      const response = await provider.offers(body.kind,{ currencyFrom,currencyTo,amountFrom:body.amount,country:body.country,...(body.state ? {state:body.state} : {}),ip,externalUserId:walletId });
      const offers = Array.isArray(response) ? response : response.offers; requireValue(Array.isArray(offers), 'Invalid offers'); const quotes = [];
      for (const offer of offers) {
        if (!supportedProviders.has(offer.providerCode) || !asset.providers?.some(p=>p.providerCode === offer.providerCode && flows(p,body.kind)) || !country.providers?.some(p=>p.providerCode === offer.providerCode && flows(p,body.kind)) || !fiat.providers?.some(p=>p.providerCode === offer.providerCode && flows(p,body.kind))) continue;
        for (const method of offer.paymentMethodOffer || []) {
          if (!positive(method.amountExpectedTo) || !exchangeDecimal(method.fee) || typeof method.method !== 'string' || !/^[a-z0-9_-]{1,80}$/i.test(method.method)) continue;
          const quote = { ...body,currencyFrom,currencyTo,walletAddress:address,providerCode:offer.providerCode,paymentMethod:method.method,amountTo:method.amountExpectedTo,fee:method.fee };
          const id = randomUUID(), saved = await db.query('INSERT INTO changelly_fiat_quotes(id,wallet_id,account_id,quote) VALUES($1,$2,$3,$4) RETURNING expires_at',[id,walletId,accountId,quote]);
          quotes.push({ id,...quote,expiresAt:saved.rows[0].expires_at });
          if (quotes.length >= 30) return { quotes };
        }
      }
      return { quotes };
    },
    async create(walletId,accountId,body,ip) {
      enabled(); exactKeys(body,['quoteId','idempotencyKey']); requireValue(uuidPattern.test(body.quoteId) && uuidPattern.test(body.idempotencyKey), 'Invalid order ID');
      await account(db,walletId,accountId);
      const previous = (await db.query(`${select} WHERE o.wallet_id=$1 AND o.idempotency_key=$2`,[walletId,body.idempotencyKey])).rows[0];
      if (previous) { requireValue(previous.account_id === accountId && previous.quote_id === body.quoteId,'Order key already used'); return present(previous); }
      const quoteRow = (await db.query('SELECT quote FROM changelly_fiat_quotes WHERE id=$1 AND wallet_id=$2 AND account_id=$3 AND expires_at>clock_timestamp()',[body.quoteId,walletId,accountId])).rows[0];
      if (!quoteRow) throw new ApiError(409,'QUOTE_EXPIRED','Review this order again'); const q = quoteRow.quote;
      requireValue(same(await wallet(walletId,accountId,q.cryptoCurrency,q.network),q.walletAddress), 'Wallet changed');
      const id = randomUUID(); let row;
      try { row = (await db.query('INSERT INTO changelly_fiat_orders(id,wallet_id,account_id,quote_id,idempotency_key,environment) VALUES($1,$2,$3,$4,$5,$6) RETURNING *',[id,walletId,accountId,body.quoteId,body.idempotencyKey,config.changellyFiatEnvironment])).rows[0]; }
      catch (error) { if (error.code === '23505') throw new ApiError(409,'IDEMPOTENCY_CONFLICT','Check the existing order'); throw error; }
      try {
        const payload = { externalOrderId:id,externalUserId:walletId,providerCode:q.providerCode,currencyFrom:q.currencyFrom,currencyTo:q.currencyTo,amountFrom:q.kind === 'sell' ? JSON.rawJSON(q.amount) : q.amount,country:q.country,...(q.state ? {state:q.state} : {}),ip,paymentMethod:q.paymentMethod,...(q.kind === 'buy' ? {walletAddress:q.walletAddress} : {refundAddress:q.walletAddress}) };
        const result = await provider.create(q.kind,payload), checkout = fiatCheckoutUrl(result.redirectUrl, config.changellyFiatEnvironment);
        requireValue(result.externalOrderId === id && result.externalUserId === walletId && result.type === q.kind && result.currencyFrom === q.currencyFrom && result.currencyTo === q.currencyTo && result.providerCode === q.providerCode && same(q.kind === 'buy' ? result.walletAddress : result.refundAddress,q.walletAddress) && positive(result.amountFrom) && parseUnits(result.amountFrom,36) === parseUnits(q.amount,36) && typeof result.orderId === 'string' && result.orderId.length <= 200 && checkout, 'Invalid checkout response');
        row = (await db.query("UPDATE changelly_fiat_orders SET status='awaiting_payment',provider_id=$2,checkout_url=$3,updated_at=clock_timestamp() WHERE id=$1 RETURNING *",[id,result.orderId,checkout])).rows[0];
      } catch { row = (await db.query("UPDATE changelly_fiat_orders SET status='ambiguous',updated_at=clock_timestamp() WHERE id=$1 RETURNING *",[id])).rows[0]; }
      return present({ ...row,quote:q });
    },
    async get(walletId,id) { return present(await refresh(await load(walletId,id))); },
    async list(walletId,accountId) { await account(db,walletId,accountId); return { orders: (await db.query(`${select} WHERE o.wallet_id=$1 AND o.account_id=$2 ORDER BY o.created_at DESC LIMIT 50`,[walletId,accountId])).rows.map(present) }; },
    async reconcile() { const rows = (await db.query(`${select} WHERE o.status IN ('creating','ambiguous','created','awaiting_payment','processing') ORDER BY o.updated_at LIMIT 10`)).rows; for (const row of rows) await refresh(row); return { checked: rows.length }; },
  };
}
