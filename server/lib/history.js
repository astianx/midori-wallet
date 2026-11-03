import { getAddress } from 'ethers';
import { ApiError, requireValue, notFound } from './errors.js';
import { account } from './repositories.js';

export async function history(client, config, walletId, accountId, query = {}, eventId) {
  const saved = await account(client, walletId, accountId);
  const contract = typeof query.contract === 'string' ? query.contract.toLowerCase() : null;
  requireValue(!contract || saved.assets.some(asset => asset.contract.toLowerCase() === contract), 'Unsupported history token');
  requireValue(!query.limit || /^[1-9][0-9]?$/.test(query.limit), 'Invalid page size');
  const limit = Number(query.limit || 30);
  const { rows: states } = await client.query('SELECT next_block::text,backfill_next::text,backfill_end::text,last_head::text,revision::text,updated_at FROM index_cursors WHERE account_id=$1', [accountId]);
  const state = states[0];
  let cursor;
  if (query.cursor) {
    try {
      requireValue(typeof query.cursor === 'string' && query.cursor.length <= 1000);
      cursor = JSON.parse(Buffer.from(query.cursor, 'base64url').toString('utf8'));
      requireValue(cursor.accountId === accountId && cursor.contract === contract && cursor.revision === (state?.revision || '0'));
      requireValue(['snapshot', 'block', 'log', 'id'].every(key => typeof cursor[key] === 'string' && /^(0|[1-9][0-9]{0,19})$/.test(cursor[key]) && BigInt(cursor[key]) <= 9223372036854775807n));
      requireValue(BigInt(cursor.log) <= 2147483647n);
    } catch { throw new ApiError(409, 'HISTORY_REFRESH_REQUIRED', 'History changed; refresh to start a new page'); }
  }
  if (eventId) requireValue(/^[1-9][0-9]{0,18}$/.test(eventId), 'Invalid history event');
  const snapshot = cursor?.snapshot || (await client.query('SELECT COALESCE(max(event_id),0)::text AS value FROM event_participants WHERE account_id=$1', [accountId])).rows[0].value;
  const { rows } = await client.query(`SELECT e.*,e.id::text AS event_id,e.amount_atomic::text AS atomic,e.block_number::text AS block,p.incoming,p.outgoing,ar.symbol,ar.decimals
    FROM event_participants p JOIN chain_events e ON e.id=p.event_id JOIN asset_registry ar USING(chain_id,contract)
    WHERE p.account_id=$1 AND ($2::text IS NULL OR e.contract=$2) AND e.id<=$3::bigint
    AND ($4::bigint IS NULL OR (e.block_number,e.log_index,e.id)<($4::bigint,$5::integer,$6::bigint))
    AND ($7::bigint IS NULL OR e.id=$7::bigint)
    ORDER BY e.block_number DESC,e.log_index DESC,e.id DESC LIMIT $8`, [accountId, contract, snapshot, cursor?.block || null, cursor?.log || null, cursor?.id || null, eventId || null, limit + 1]);
  if (eventId && !rows.length) throw notFound();
  const lastIndexed = state ? BigInt(state.next_block) - 1n : -1n;
  const observed = state && BigInt(state.last_head) < lastIndexed ? BigInt(state.last_head) : lastIndexed;
  const entries = rows.slice(0, limit).map(row => {
    const confirmations = row.canonical && observed >= BigInt(row.block) ? observed - BigInt(row.block) + 1n : 0n;
    return { id: row.event_id, transactionHash: row.transaction_hash, logIndex: row.log_index, chainId: row.chain_id, contract: getAddress(row.contract), symbol: row.symbol, decimals: row.decimals, amountAtomic: row.atomic, from: getAddress(row.sender), to: getAddress(row.recipient), incoming: row.incoming, outgoing: row.outgoing, status: !row.canonical ? 'reorganized' : confirmations >= BigInt(config.indexConfirmations) ? 'confirmed' : 'included', confirmations: confirmations.toString(), minConfirmations: config.indexConfirmations, timestamp: row.occurred_at, origin: 'unverified', midoriFeeAtomic: '0' };
  });
  const more = rows.length > limit;
  const last = rows[Math.min(rows.length, limit) - 1];
  return { transactions: entries, hasMore: more, cursor: more ? Buffer.from(JSON.stringify({ accountId, contract, revision: state?.revision || '0', snapshot, block: last.block, log: String(last.log_index), id: last.event_id })).toString('base64url') : null,
    indexing: { enabled: config.indexEnabled, status: !state ? 'waiting' : Date.now() - new Date(state.updated_at).getTime() > Math.max(config.workerIntervalMs * 3, 120000) ? 'stale' : BigInt(state.backfill_next) < BigInt(state.backfill_end) || BigInt(state.next_block) <= BigInt(state.last_head) ? 'syncing' : 'caught_up', lastIndexedBlock: lastIndexed < 0n ? null : lastIndexed.toString(), updatedAt: state?.updated_at || null } };
}
