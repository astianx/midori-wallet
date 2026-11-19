import { randomUUID } from 'node:crypto';
import { getAddress } from 'ethers';
import { canonical, randomToken, sha256, uuidPattern } from './crypto.js';
import { exactKeys, requireValue, notFound } from './errors.js';
import { account, limitAttempts } from './repositories.js';
import { history } from './history.js';
import { publicOrder } from './ramp.js';

function decimal(value, decimals = 18) {
  if (value == null) return null;
  const text = String(value);
  requireValue(/^(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(text), 'Invalid recorded amount');
  const [whole, fraction = ''] = text.split('.');
  requireValue(fraction.length <= decimals, 'Invalid recorded precision');
  return whole + (fraction.replace(/0+$/, '') ? '.' + fraction.replace(/0+$/, '') : '');
}
function atomic(value, decimals) {
  requireValue(typeof value === 'string' && /^\d+$/.test(value) && Number.isInteger(decimals) && decimals >= 0 && decimals <= 36, 'Invalid recorded amount');
  const digits = BigInt(value).toString().padStart(decimals + 1, '0');
  return decimal(decimals ? digits.slice(0, -decimals) + '.' + digits.slice(-decimals) : digits, decimals);
}
function date(value) { const parsed = new Date(value); requireValue(Number.isFinite(parsed.valueOf()), 'Recorded date unavailable'); return parsed.toISOString(); }
async function party(db, address, chainId = 8453) {
  if (!address) return null;
  const normalized = getAddress(address);
  const { rows } = await db.query(`SELECT t.alias FROM wallet_tags t JOIN chain_accounts a ON a.id=t.account_id WHERE a.chain_id=$1 AND a.address=$2 AND t.active`, [chainId, normalized.toLowerCase()]);
  return { address: normalized, tag: rows[0]?.alias ? `$${rows[0].alias}` : null };
}
async function sourceRecord(db, config, walletId, accountId, source, reference) {
  if (source === 'payment') {
    requireValue(/^(?:e[1-9][0-9]{0,18}|p[0-9a-f-]{36})$/.test(reference), 'Invalid movement reference');
    return (await history(db, config, walletId, accountId, {}, reference)).transactions[0];
  }
  requireValue(source === 'ramp' && uuidPattern.test(reference), 'Invalid order reference');
  const { rows } = await db.query('SELECT *,fiat_amount::text,crypto_amount::text FROM fiat_orders WHERE id=$1 AND wallet_id=$2 AND account_id=$3', [reference, walletId, accountId]);
  if (!rows[0]) throw notFound();
  return publicOrder(rows[0]);
}
export async function receiptDocument(db, source, item, saved, id = randomUUID(), now = new Date()) {
  const owner = source === 'ramp' && item.network === 'bitcoin' ? { address: item.walletAddress, tag: null } : await party(db, saved.address);
  if (source === 'ramp') {
    const fiat = item.fiatAmount == null ? null : { value: decimal(item.fiatAmount), currency: item.fiatCurrency };
    const crypto = item.cryptoAmount == null ? null : { value: decimal(item.cryptoAmount), currency: item.cryptoCurrency };
    return { version: 1, id, source, reference: item.id, kind: item.kind, symbol: item.cryptoCurrency, status: item.status,
      environment: item.environment, network: item.network === 'bitcoin' ? 'Bitcoin' : item.environment === 'STAGING' ? 'Base Sepolia' : 'Base',
      amount: item.kind === 'buy' ? fiat : crypto, paid: item.kind === 'buy' ? fiat : crypto, received: item.kind === 'buy' ? crypto : fiat,
      from: item.kind === 'sell' ? owner : null, to: item.kind === 'buy' ? owner : null, owner,
      date: date(item.createdAt), issuedAt: date(now), transactionHash: item.transactionHash || null, midoriFee: null, networkCost: null };
  }
  const [from, to] = await Promise.all([party(db, item.from, item.chainId), party(db, item.to, item.chainId)]);
  return { version: 1, id, source, reference: item.id, kind: item.incoming ? 'receive' : 'send', symbol: item.symbol,
    status: item.status, environment: 'PRODUCTION', network: 'Base',
    amount: { value: atomic(item.amountAtomic, item.decimals), currency: item.symbol }, paid: null, received: null,
    from, to, owner, date: date(item.timestamp), issuedAt: date(now), transactionHash: item.transactionHash || null,
    midoriFee: item.origin === 'midori' && item.midoriFeeAtomic != null ? { value: atomic(item.midoriFeeAtomic, item.decimals), currency: item.symbol } : null,
    networkCost: item.feeMode === 'sponsored' ? { sponsored: true } : item.actualFeeAtomic == null ? null : { value: atomic(String(item.actualFeeAtomic), 6), currency: item.symbol } };
}
export function receiptService(config) {
  const receiptPath = config.receiptPath || '/receipt';
  requireValue(typeof receiptPath === 'string' && receiptPath.startsWith('/') && !receiptPath.startsWith('//') && !/[?#]/.test(receiptPath), 'Invalid receipt path');
  return {
    async issue(db, principal, accountId, body) {
      exactKeys(body, ['source', 'reference']);
      requireValue(['payment', 'ramp'].includes(body.source) && typeof body.reference === 'string' && body.reference.length <= 80);
      const saved = await account(db, principal.wallet_id, accountId);
      await limitAttempts(db, `receipt-issue:${principal.wallet_id}`, 20);
      const record = await sourceRecord(db, config, principal.wallet_id, accountId, body.source, body.reference);
      const document = await receiptDocument(db, body.source, record, saved), token = randomToken();
      const digest = sha256(canonical(document));
      await db.query('INSERT INTO receipt_shares(id,wallet_id,account_id,source,reference,token_hash,payload,payload_hash) VALUES($1,$2,$3,$4,$5,$6,$7,$8)', [document.id, principal.wallet_id, accountId, body.source, body.reference, sha256(token), document, digest]);
      const link = new URL(receiptPath, config.publicOrigin); link.hash = token;
      return { document, verificationUrl: link.href, digest };
    },
    async verify(db, body) {
      exactKeys(body, ['token']);
      if (typeof body.token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(body.token)) throw notFound();
      const { rows } = await db.query('SELECT wallet_id,account_id,source,reference,payload,payload_hash FROM receipt_shares WHERE token_hash=$1', [sha256(body.token)]);
      const row = rows[0]; if (!row || sha256(canonical(row.payload)) !== row.payload_hash) throw notFound();
      let currentStatus = null;
      try { currentStatus = (await sourceRecord(db, config, row.wallet_id, row.account_id, row.source, row.reference)).status; } catch { /* A snapshot is not a guarantee of a currently completed operation. */ }
      return { document: row.payload, digest: row.payload_hash, currentStatus, checkedAt: new Date().toISOString() };
    },
  };
}
