import { getAddress, verifyMessage } from 'ethers';
import { bitcoinReader, bitcoinBindingMessage, validateBitcoinAddress, bitcoinTransactionId, bitcoinOutputs, parseTokenAmount } from '../../packages/wallet-core/src/index.js';
import { ApiError, exactKeys, requireValue } from './errors.js';
import { account } from './repositories.js';
import { uuidPattern } from './crypto.js';

const offlineClient = { async connect() {}, async close() {} };
export async function bitcoinAddress(db, walletId, accountId) {
  const { rows } = await db.query('SELECT address FROM bitcoin_accounts WHERE wallet_id=$1 AND account_id=$2', [walletId, accountId]);
  return rows[0]?.address;
}
export async function registerBitcoin(db, principal, accountId, body) {
  exactKeys(body, ['address', 'signature', 'ownerSignature']);
  requireValue(validateBitcoinAddress(body.address) && /^bc1q[ac-hj-np-z02-9]{38}$/.test(body.address), 'Invalid Bitcoin SegWit address');
  requireValue(typeof body.signature === 'string' && body.signature.length < 200 && typeof body.ownerSignature === 'string' && /^0x[0-9a-fA-F]{130}$/.test(body.ownerSignature), 'Invalid Bitcoin ownership proof');
  const saved = await account(db, principal.wallet_id, accountId);
  const message = bitcoinBindingMessage(principal.wallet_id, accountId, saved.address, body.address);
  let valid = false;
  try { valid = getAddress(verifyMessage(message, body.ownerSignature)) === getAddress(saved.descriptor.ownerAddress) && await bitcoinReader(body.address, offlineClient).verify(message, body.signature); } catch { /* Invalid proofs are rejected below. */ }
  requireValue(valid, 'Invalid Bitcoin ownership proof');
  await db.query('INSERT INTO bitcoin_accounts(wallet_id,account_id,address) VALUES($1,$2,$3) ON CONFLICT(account_id) DO NOTHING', [principal.wallet_id, accountId, body.address]);
  const address = await bitcoinAddress(db, principal.wallet_id, accountId);
  requireValue(address === body.address, 'Bitcoin account does not match the existing wallet');
  return { address, network: 'bitcoin' };
}

export function bitcoinGateway(fetchImpl = fetch) {
  const pending = new Map(), cache = new Map();
  return async (path, raw) => {
    const address = path.split('/')[1];
    const permitted = /^(blocks\/tip\/height|fee-estimates|tx\/[a-f0-9]{64}(\/hex)?)$/.test(path)
      || /^address\/[^/]+(\/(utxo|txs(\/chain\/[a-f0-9]{64})?))?$/.test(path) && validateBitcoinAddress(address);
    requireValue(raw === undefined ? permitted : path === 'tx' && typeof raw === 'string' && /^(?:[a-f0-9]{2}){10,200000}$/.test(raw), 'Invalid Bitcoin request');
    if (!raw && cache.get(path)?.expires > Date.now()) return cache.get(path).value;
    if (!raw && pending.has(path)) return pending.get(path);
    const request = (async () => {
      try {
        const response = await fetchImpl(`https://blockstream.info/api/${path}`, { ...(raw ? { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: raw } : {}), redirect: 'error', signal: AbortSignal.timeout(15000) });
        if (!response.ok) throw new Error();
        const value = /\/hex$|^blocks\/|^tx$/.test(path) ? await response.text() : await response.json();
        if (!raw) { if (cache.size >= 500) cache.delete(cache.keys().next().value); cache.set(path, { value, expires: Date.now() + (/^tx\/.+\/hex$/.test(path) ? 300000 : 10000) }); }
        return value;
      } catch { throw new ApiError(503, 'CHAIN_UNAVAILABLE', 'Bitcoin is temporarily unavailable'); }
      finally { pending.delete(path); }
    })();
    if (!raw) pending.set(path, request);
    return request;
  };
}

export async function broadcastBitcoin(db, principal, body, gateway) {
  exactKeys(body, ['raw'], ['exchangeOrderId']);
  requireValue(typeof body.raw === 'string' && /^(?:[a-f0-9]{2}){10,200000}$/.test(body.raw), 'Invalid Bitcoin transaction');
  if (!body.exchangeOrderId) return gateway('tx', body.raw);
  requireValue(uuidPattern.test(body.exchangeOrderId), 'Invalid exchange ID');
  const hash = bitcoinTransactionId(body.raw);
  await db.transaction(async client => {
    const { rows } = await client.query('SELECT o.*,q.quote FROM exchange_orders o JOIN exchange_quotes q ON q.id=o.quote_id WHERE o.id=$1 AND o.wallet_id=$2 FOR UPDATE OF o', [body.exchangeOrderId, principal.wallet_id]);
    const order = rows[0];
    requireValue(order && order.status === 'waiting' && order.quote.from.ticker === 'btc' && !order.spark_deposit_id, 'Unsupported exchange deposit');
    requireValue(!order.bitcoin_deposit_hash || order.bitcoin_deposit_hash === hash, 'A deposit is already being confirmed');
    const outputs = bitcoinOutputs(body.raw).filter(output => output.address === order.payin_address);
    requireValue(outputs.length === 1 && BigInt(outputs[0].value) === parseTokenAmount(order.quote.amountFrom, 8), 'Exchange deposit changed');
    await client.query('UPDATE exchange_orders SET bitcoin_deposit_hash=$2,updated_at=clock_timestamp() WHERE id=$1', [order.id, hash]);
  });
  return gateway('tx', body.raw);
}
