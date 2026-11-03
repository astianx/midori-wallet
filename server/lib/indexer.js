import { Interface } from 'ethers';
import { chainRpc } from './chain-rpc.js';

const transfer = new Interface(['event Transfer(address indexed from,address indexed to,uint256 value)']);
const topic = transfer.getEvent('Transfer').topicHash;
const hex = value => `0x${BigInt(value).toString(16)}`;
const hash = value => typeof value === 'string' && /^0x[0-9a-fA-F]{64}$/.test(value);

async function blockAt(rpc, number) {
  const block = await rpc('eth_getBlockByNumber', [hex(number), false]);
  if (!hash(block.hash) || BigInt(block.number) !== BigInt(number)) throw new Error('Invalid canonical block');
  return block;
}

async function repairReorganization(db, rpc, accountId, head) {
  const { rows } = await db.query('SELECT block_number::text,block_hash FROM index_checkpoints WHERE account_id=$1 ORDER BY block_number DESC', [accountId]);
  if (!rows.length || (BigInt(rows[0].block_number) <= head && (await blockAt(rpc, rows[0].block_number)).hash.toLowerCase() === rows[0].block_hash)) return false;
  let ancestor = -1n;
  for (const saved of rows.slice(1)) {
    if (BigInt(saved.block_number) > head) continue;
    const block = await blockAt(rpc, saved.block_number);
    if (block.hash.toLowerCase() === saved.block_hash) { ancestor = BigInt(saved.block_number); break; }
  }
  await db.transaction(async client => {
    await client.query('SELECT pg_advisory_xact_lock(738194261)');
    await client.query('UPDATE chain_events SET canonical=false WHERE canonical AND block_number>$1', [ancestor.toString()]);
    await client.query(`UPDATE index_cursors SET next_block=LEAST(next_block,$1),backfill_next=LEAST(backfill_next,$1),
      last_head=LEAST(last_head,GREATEST($1-1,0)),revision=revision+1,updated_at=clock_timestamp()`, [(ancestor + 1n).toString()]);
    await client.query('DELETE FROM index_checkpoints WHERE block_number>$1', [ancestor.toString()]);
  });
  return true;
}

async function readRange(rpc, saved, contracts, from, to) {
  const end = await blockAt(rpc, to);
  const padded = `0x${saved.address.slice(2).padStart(64, '0')}`;
  const logs = [];
  for (const contract of contracts) {
    for (const topics of [[topic, padded], [topic, null, padded]]) {
      const batch = await rpc('eth_getLogs', [{ address: contract, fromBlock: hex(from), toBlock: hex(to), topics }]);
      if (!Array.isArray(batch)) throw new Error('Invalid log batch');
      logs.push(...batch);
    }
  }
  const events = new Map();
  const blocks = new Map([[to.toString(), end]]);
  const receipts = new Map();
  for (const log of logs) {
    const blockNumber = BigInt(log.blockNumber);
    if (log.removed || !hash(log.transactionHash) || !hash(log.blockHash) || blockNumber < from || blockNumber > to || !contracts.includes(log.address.toLowerCase())) throw new Error('Invalid transfer log');
    const decoded = transfer.parseLog(log);
    if (!decoded || decoded.name !== 'Transfer') throw new Error('Invalid transfer event');
    const sender = decoded.args.from.toLowerCase(), recipient = decoded.args.to.toLowerCase();
    if (![sender, recipient].includes(saved.address)) throw new Error('Unexpected transfer participant');
    const logIndex = Number(BigInt(log.logIndex));
    if (!Number.isSafeInteger(logIndex) || logIndex < 0) throw new Error('Invalid log index');
    const blockKey = blockNumber.toString();
    if (!blocks.has(blockKey)) blocks.set(blockKey, await blockAt(rpc, blockNumber));
    const block = blocks.get(blockKey);
    if (block.hash.toLowerCase() !== log.blockHash.toLowerCase()) throw new Error('Reorganized log batch');
    const tx = log.transactionHash.toLowerCase();
    if (!receipts.has(tx)) receipts.set(tx, await rpc('eth_getTransactionReceipt', [tx]));
    const receipt = receipts.get(tx);
    if (receipt.status !== '0x1' || receipt.transactionHash?.toLowerCase() !== tx || receipt.blockHash?.toLowerCase() !== block.hash.toLowerCase() || BigInt(receipt.blockNumber) !== blockNumber) throw new Error('Invalid transfer receipt');
    if (!receipt.logs?.some(item => item.transactionHash?.toLowerCase() === tx && item.blockHash?.toLowerCase() === log.blockHash.toLowerCase() && BigInt(item.logIndex) === BigInt(logIndex) && item.address.toLowerCase() === log.address.toLowerCase() && item.data === log.data && JSON.stringify(item.topics) === JSON.stringify(log.topics))) throw new Error('Transfer missing from receipt');
    events.set(`${tx}:${logIndex}`, { tx, logIndex, blockNumber: blockKey, blockHash: block.hash.toLowerCase(), contract: log.address.toLowerCase(), sender, recipient, amount: decoded.args.value.toString(), occurredAt: new Date(Number(BigInt(block.timestamp)) * 1000).toISOString(), receipt: { transactionHash: tx, blockHash: block.hash.toLowerCase(), status: receipt.status, gasUsed: receipt.gasUsed, effectiveGasPrice: receipt.effectiveGasPrice } });
  }
  if ((await blockAt(rpc, to)).hash !== end.hash) throw new Error('Reorganized log batch');
  return { events: [...events.values()], end };
}

async function persistRange(db, saved, range, from, to, head, backfill) {
  return db.transaction(async client => {
    await client.query('SELECT pg_advisory_xact_lock(738194261)');
    const { rows } = await client.query('SELECT * FROM index_cursors WHERE account_id=$1 FOR UPDATE', [saved.account_id]);
    const current = rows[0];
    if (String(current.revision) !== String(saved.revision) || BigInt(backfill ? current.backfill_next : current.next_block) !== from) return false;
    for (const event of range.events) {
      const result = await client.query(`INSERT INTO chain_events(chain_id,contract,transaction_hash,log_index,block_number,block_hash,sender,recipient,amount_atomic,occurred_at,receipt)
        VALUES (8453,$1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
        ON CONFLICT (chain_id,transaction_hash,log_index) DO UPDATE SET block_number=EXCLUDED.block_number,block_hash=EXCLUDED.block_hash,
        contract=EXCLUDED.contract,sender=EXCLUDED.sender,recipient=EXCLUDED.recipient,amount_atomic=EXCLUDED.amount_atomic,
        occurred_at=EXCLUDED.occurred_at,receipt=EXCLUDED.receipt,canonical=true RETURNING id`,
      [event.contract, event.tx, event.logIndex, event.blockNumber, event.blockHash, event.sender, event.recipient, event.amount, event.occurredAt, event.receipt]);
      await client.query(`INSERT INTO event_participants(account_id,event_id,incoming,outgoing)
        SELECT id,$1,address=$2,address=$3 FROM chain_accounts WHERE chain_id=8453 AND address IN ($2,$3)
        ON CONFLICT (account_id,event_id) DO UPDATE SET incoming=EXCLUDED.incoming,outgoing=EXCLUDED.outgoing`, [result.rows[0].id, event.recipient, event.sender]);
    }
    await client.query('INSERT INTO index_checkpoints(account_id,block_number,block_hash) VALUES ($1,$2,$3) ON CONFLICT (account_id,block_number) DO UPDATE SET block_hash=EXCLUDED.block_hash', [saved.account_id, to.toString(), range.end.hash.toLowerCase()]);
    await client.query(`UPDATE index_cursors SET ${backfill ? 'backfill_next' : 'next_block'}=$2,last_head=$3,updated_at=clock_timestamp() WHERE account_id=$1`, [saved.account_id, (to + 1n).toString(), head.toString()]);
    return true;
  });
}

export async function synchronizeHistory(db, config, rpc = chainRpc(config)) {
  if (!config.indexEnabled) return { enabled: false };
  if (BigInt(await rpc('eth_chainId')) !== 8453n) throw new Error('History RPC must be Base');
  const head = BigInt(await rpc('eth_blockNumber'));
  const start = BigInt(config.indexStartBlock);
  const recent = head > BigInt(config.indexRecentBlocks) ? head - BigInt(config.indexRecentBlocks) : 0n;
  const liveStart = recent > start ? recent : start;
  await db.query(`INSERT INTO index_cursors(account_id,next_block,backfill_next,backfill_end,last_head)
    SELECT id,$1,$2,$1,$3 FROM chain_accounts WHERE chain_id=8453 ON CONFLICT DO NOTHING`, [liveStart.toString(), start.toString(), head.toString()]);
  const { rows: assets } = await db.query('SELECT contract FROM asset_registry WHERE chain_id=8453 AND enabled');
  const contracts = assets.map(asset => asset.contract);
  const { rows: accounts } = await db.query('SELECT c.*,a.address FROM index_cursors c JOIN chain_accounts a ON a.id=c.account_id ORDER BY c.updated_at LIMIT $1', [config.indexAccountBatch]);
  let ranges = 0;
  for (const saved of accounts) {
    if (await repairReorganization(db, rpc, saved.account_id, head)) continue;
    const cursorHead = await db.query('UPDATE index_cursors SET last_head=$2,updated_at=clock_timestamp() WHERE account_id=$1 AND revision=$3 RETURNING account_id', [saved.account_id, head.toString(), saved.revision]);
    if (!cursorHead.rowCount) continue;
    for (const backfill of [false, true]) {
      const from = BigInt(backfill ? saved.backfill_next : saved.next_block);
      const limit = backfill ? BigInt(saved.backfill_end) - 1n : head;
      if (from > limit) continue;
      const proposed = from + BigInt(config.indexBlockBatch) - 1n;
      const to = proposed < limit ? proposed : limit;
      const range = await readRange(rpc, saved, contracts, from, to);
      const { rows: anchor } = await db.query('SELECT block_number::text,block_hash FROM index_checkpoints WHERE account_id=$1 ORDER BY block_number DESC LIMIT 1', [saved.account_id]);
      if (anchor.length && (await blockAt(rpc, anchor[0].block_number)).hash.toLowerCase() !== anchor[0].block_hash) throw new Error('Chain changed during synchronization');
      if (await persistRange(db, saved, range, from, to, head, backfill)) ranges++;
    }
  }
  return { enabled: true, accounts: accounts.length, ranges, head: head.toString() };
}
