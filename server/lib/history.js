import { getAddress } from 'ethers';
import { ApiError, requireValue, notFound } from './errors.js';
import { account } from './repositories.js';

export async function history(client, config, walletId, accountId, query = {}, eventId) {
  const saved=await account(client,walletId,accountId),contract=typeof query.contract==='string'?query.contract.toLowerCase():null;
  requireValue(!contract||saved.assets.some(asset=>asset.contract.toLowerCase()===contract),'Unsupported history token');
  requireValue(!query.limit||/^[1-9][0-9]?$/.test(query.limit),'Invalid page size');
  const limit=Number(query.limit||30);
  const state=(await client.query('SELECT next_block::text,backfill_next::text,backfill_end::text,last_head::text,revision::text,updated_at FROM index_cursors WHERE account_id=$1',[accountId])).rows[0];
  let cursor;
  if(query.cursor) {
    try {
      requireValue(typeof query.cursor==='string'&&query.cursor.length<=1600);cursor=JSON.parse(Buffer.from(query.cursor,'base64url').toString());
      requireValue(cursor.accountId===accountId&&cursor.contract===contract&&cursor.revision===(state?.revision||'0'));
      requireValue(typeof cursor.snapshot==='string'&&/^[0-9]{1,19}$/.test(cursor.snapshot)&&BigInt(cursor.snapshot)<=9223372036854775807n);
      requireValue(typeof cursor.time==='string'&&Number.isFinite(Date.parse(cursor.time))&&typeof cursor.before==='string'&&Number.isFinite(Date.parse(cursor.before))&&typeof cursor.id==='string'&&/^(?:e[1-9][0-9]{0,18}|p[0-9a-f-]{36})$/.test(cursor.id));
    } catch {throw new ApiError(409,'HISTORY_REFRESH_REQUIRED','History changed; refresh to start a new page');}
  }
  if(eventId)requireValue(/^(?:[1-9][0-9]{0,18}|e[1-9][0-9]{0,18}|p[0-9a-f-]{36})$/.test(eventId),'Invalid history entry');
  const snapshot=cursor?.snapshot||(await client.query('SELECT COALESCE(max(event_id),0)::text AS value FROM event_participants WHERE account_id=$1',[accountId])).rows[0].value;
  const before=cursor?.before||(await client.query(`SELECT to_char(clock_timestamp() AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS now`)).rows[0].now;
  const indexed=state?BigInt(state.next_block)-1n:-1n;
  const observed=state&&BigInt(state.last_head)<indexed?BigInt(state.last_head):indexed;
  const rows=(await client.query(`WITH movements AS (
    SELECT 'e'||e.id::text AS id,e.occurred_at AS sort_time,e.chain_id,e.contract,e.transaction_hash,e.log_index,e.sender,e.recipient,e.amount_atomic::text,
      p.incoming,p.outgoing,e.canonical,e.block_number,
      CASE WHEN NOT e.canonical THEN 'reorganized' WHEN $11::bigint-e.block_number+1>=$12 THEN 'confirmed' ELSE 'included' END AS state,
      CASE WHEN e.intent_id IS NOT NULL AND e.event_kind='payment' THEN 'midori' ELSE 'unverified' END AS origin,
      e.intent_id,NULL::text AS fee_mode,NULL::text AS fee_token,NULL::text AS actual_fee,NULL::text AS max_fee,NULL::text AS user_operation_hash,NULL::text AS recipient_tag,'settled'::text AS accounting_state
    FROM event_participants p JOIN chain_events e ON e.id=p.event_id
    WHERE p.account_id=$1 AND e.id<=$3::bigint AND e.event_kind<>'gas'
      AND NOT(e.event_kind='payment' AND e.sender=$10 AND e.intent_id IS NOT NULL)
    UNION ALL
    SELECT 'p'||i.id::text,i.created_at,i.chain_id,i.contract,u.transaction_hash,COALESCE(u.payment_log_index,0),i.sender,i.recipient,i.amount_atomic::text,
      false,true,i.status NOT IN ('reorganized'),u.block_number,
      CASE WHEN i.status IN ('authorized','submitted','ambiguous') THEN 'pending' WHEN i.status IN ('confirmed','included') AND EXISTS(SELECT 1 FROM chain_events e WHERE e.intent_id=i.id AND NOT e.canonical AND e.block_hash=u.block_hash) THEN 'reorganized' ELSE i.status END,
      'midori',i.id,i.fee_mode,i.fee_token,u.actual_fee_atomic::text,i.quote->>'maxFeeAtomic',u.user_operation_hash,i.recipient_tag,u.accounting_state
    FROM tx_intents i JOIN user_operations u ON u.intent_id=i.id WHERE i.account_id=$1 AND i.created_at<=$4::timestamptz
  ) SELECT m.*,ar.symbol,ar.decimals,to_char(m.sort_time AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"') AS sort_cursor FROM movements m JOIN asset_registry ar USING(chain_id,contract)
    WHERE ($2::text IS NULL OR m.contract=$2) AND ($5::timestamptz IS NULL OR (m.sort_time,m.id)<($5::timestamptz,$6::text))
    AND ($7::text IS NULL OR m.id=$7) AND $9::boolean ORDER BY m.sort_time DESC,m.id DESC LIMIT $8`,[accountId,contract,snapshot,before,cursor?.time||null,cursor?.id||null,eventId?(/^[0-9]/.test(eventId)?`e${eventId}`:eventId):null,limit+1,true,saved.address.toLowerCase(),observed.toString(),config.indexConfirmations])).rows;
  if(eventId&&!rows.length)throw notFound();
  const entries=rows.slice(0,limit).map(row=>({id:row.id,transactionHash:row.transaction_hash,logIndex:row.log_index,chainId:row.chain_id,contract:getAddress(row.contract),symbol:row.symbol,decimals:row.decimals,amountAtomic:row.amount_atomic,from:getAddress(row.sender),to:getAddress(row.recipient),incoming:row.incoming,outgoing:row.outgoing,status:row.state,confirmations:row.canonical&&row.block_number!=null&&observed>=BigInt(row.block_number)?(observed-BigInt(row.block_number)+1n).toString():'0',minConfirmations:config.indexConfirmations,timestamp:row.sort_time,origin:row.origin,midoriFeeAtomic:'0',intentId:row.intent_id,feeMode:row.fee_mode,feeToken:row.fee_token,actualFeeAtomic:row.actual_fee,maxFeeAtomic:row.max_fee,userOperationHash:row.user_operation_hash,recipientTag:row.recipient_tag,accountingState:row.accounting_state}));
  const hasMore=rows.length>limit,last=rows[Math.min(rows.length,limit)-1];
  return {transactions:entries,hasMore,cursor:hasMore?Buffer.from(JSON.stringify({accountId,contract,revision:state?.revision||'0',snapshot,before,time:last.sort_cursor,id:last.id})).toString('base64url'):null,
    indexing:{enabled:config.indexEnabled,status:!state?'waiting':Date.now()-new Date(state.updated_at).getTime()>Math.max(config.workerIntervalMs*3,120000)?'stale':BigInt(state.backfill_next)<BigInt(state.backfill_end)||BigInt(state.next_block)<=BigInt(state.last_head)?'syncing':'caught_up',lastIndexedBlock:indexed<0n?null:indexed.toString(),updatedAt:state?.updated_at||null}};
}
