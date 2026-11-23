import { stablecoinNetwork } from '../../config/stablecoin-networks.js';
import { inspectEvmTransfer } from '../../packages/wallet-core/src/evm.js';
import { ApiError, exactKeys } from './errors.js';
const pendingError = () => new ApiError(409, 'PAYMENT_ALREADY_PENDING', 'A payment is still being confirmed');
export function evmPaymentService(db, gateway) {
  const present = row => row ? { hash: row.hash, quote: row.payment, status: row.status, createdAt: row.created_at } : null;
  async function status(accountId, network, owner, hash) {
    const result = await gateway.status(network, owner, hash);
    await db.query('UPDATE evm_transfers SET status=$4,updated_at=clock_timestamp() WHERE account_id=$1 AND network=$2 AND hash=$3', [accountId,network,hash.toLowerCase(),result.status]);
    return result;
  }
  async function pending(accountId, network, owner) {
    const { rows } = await db.query("SELECT * FROM evm_transfers WHERE account_id=$1 AND network=$2 AND status NOT IN ('confirmed','reverted') ORDER BY nonce LIMIT 1", [accountId,network]);
    if (!rows.length) return { transfer: null };
    let result;
    try { result = await status(accountId,network,owner,rows[0].hash); } catch { return { transfer: present(rows[0]) }; }
    return { transfer: ['confirmed','reverted'].includes(result.status) ? null : { ...present(rows[0]), status: result.status } };
  }
  return {
    pending, status,
    async retry(accountId, network, owner, body) {
      exactKeys(body,['hash']);
      const { rows } = await db.query('SELECT raw_transaction FROM evm_transfers WHERE account_id=$1 AND network=$2 AND hash=$3',[accountId,network,body.hash]);
      if (!rows[0]) throw new ApiError(404,'NOT_FOUND','Payment not found');
      return this.broadcast(accountId,network,owner,{raw:rows[0].raw_transaction});
    },
    async quote(accountId, network, owner, body) {
      if ((await pending(accountId,network,owner)).transfer) throw pendingError();
      return gateway.quote(network,owner,body);
    },
    async broadcast(accountId, networkId, owner, body) {
      exactKeys(body, ['raw']);
      const network = stablecoinNetwork(networkId);
      let parsed;
      try { parsed = inspectEvmTransfer(body.raw,network,owner); } catch { throw new ApiError(400,'INVALID_REQUEST','Invalid signed transfer'); }
      const payment = { network: networkId, chainId: network.chainId, from: owner, to: parsed.to, contract: parsed.asset.contract, symbol: parsed.asset.symbol, decimals: parsed.asset.decimals, amountAtomic: parsed.amountAtomic, nativeSymbol: network.nativeSymbol };
      await db.transaction(async client => {
        await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1,81043))', [`${accountId}:${networkId}`]);
        const { rows } = await client.query("SELECT hash FROM evm_transfers WHERE account_id=$1 AND network=$2 AND (nonce=$3 OR status NOT IN ('confirmed','reverted'))", [accountId,networkId,parsed.tx.nonce]);
        if (rows.some(row => row.hash !== parsed.hash)) throw pendingError();
        await client.query("INSERT INTO evm_transfers(account_id,network,chain_id,nonce,hash,contract,raw_transaction,payment,status) VALUES($1,$2,$3,$4,$5,$6,$7,$8,'ambiguous') ON CONFLICT DO NOTHING", [accountId,networkId,network.chainId,parsed.tx.nonce,parsed.hash,parsed.asset.contract,body.raw,payment]);
      });
      let result;
      try { result = await gateway.broadcast(networkId,owner,body); } catch { return { hash: parsed.hash,status: 'ambiguous' }; }
      await db.query('UPDATE evm_transfers SET status=$3,updated_at=clock_timestamp() WHERE account_id=$1 AND hash=$2', [accountId,parsed.hash,result.status]);
      return result;
    },
  };
}
