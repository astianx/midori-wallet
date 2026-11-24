import { stablecoinNetwork } from '../../config/stablecoin-networks.js';
import { inspectEvmTransfer } from '../../packages/wallet-core/src/evm.js';
import { ApiError, exactKeys } from './errors.js';
const pendingError = () => new ApiError(409, 'PAYMENT_ALREADY_PENDING', 'A payment is still being confirmed');
export function evmPaymentService(db, gateway) {
  const present = row => row ? { hash: row.hash, quote: row.payment, status: row.status, createdAt: row.created_at } : null;
  async function status(accountId, network, descriptor, hash) {
    const { rows } = await db.query('SELECT payment FROM evm_transfers WHERE account_id=$1 AND network=$2 AND account_address=$3 AND hash=$4', [accountId, network, descriptor.address.toLowerCase(), hash.toLowerCase()]);
    if (!rows[0]) throw new ApiError(404, 'NOT_FOUND', 'Payment not found');
    const result = await gateway.status(network, descriptor, hash, rows[0].payment);
    await db.query('UPDATE evm_transfers SET status=$4,updated_at=clock_timestamp() WHERE account_id=$1 AND network=$2 AND hash=$3', [accountId, network, hash.toLowerCase(), result.status]);
    return result;
  }
  async function pending(accountId, network, descriptor) {
    const { rows } = await db.query("SELECT * FROM evm_transfers WHERE account_id=$1 AND network=$2 AND account_address=$3 AND status NOT IN ('confirmed','reverted') ORDER BY nonce LIMIT 1", [accountId, network, descriptor.address.toLowerCase()]);
    if (!rows.length) return { transfer: null };
    let result;
    try { result = await status(accountId, network, descriptor, rows[0].hash); } catch { return { transfer: present(rows[0]) }; }
    return { transfer: ['confirmed','reverted'].includes(result.status) ? null : { ...present(rows[0]), ...result } };
  }
  return {
    pending, status,
    async retry(accountId, network, descriptor, body) {
      exactKeys(body, ['hash']);
      const { rows } = await db.query('SELECT raw_transaction FROM evm_transfers WHERE account_id=$1 AND network=$2 AND account_address=$3 AND hash=$4', [accountId, network, descriptor.address.toLowerCase(), body.hash]);
      if (!rows[0]) throw new ApiError(404, 'NOT_FOUND', 'Payment not found');
      return this.broadcast(accountId, network, descriptor, { raw: rows[0].raw_transaction });
    },
    async quote(accountId, network, descriptor, body) {
      if ((await pending(accountId, network, descriptor)).transfer) throw pendingError();
      return gateway.quote(network, descriptor, body);
    },
    async broadcast(accountId, networkId, descriptor, body) {
      exactKeys(body, ['raw']);
      const network = stablecoinNetwork(networkId);
      let parsed;
      try { parsed = inspectEvmTransfer(body.raw, network, descriptor); } catch { throw new ApiError(400, 'INVALID_REQUEST', 'Invalid signed transfer'); }
      const payment = parsed.quote, address = descriptor.address.toLowerCase();
      await db.transaction(async client => {
        await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1,81043))', [`${accountId}:${networkId}:${address}`]);
        const { rows } = await client.query("SELECT hash FROM evm_transfers WHERE account_id=$1 AND network=$2 AND account_address=$3 AND (nonce=$4 OR status NOT IN ('confirmed','reverted'))", [accountId, networkId, address, parsed.operation.nonce.toString()]);
        if (rows.some(row => row.hash !== parsed.hash)) throw pendingError();
        await client.query("INSERT INTO evm_transfers(account_id,network,chain_id,nonce,hash,contract,raw_transaction,payment,status,account_address) VALUES($1,$2,$3,$4,$5,$6,$7,$8,'ambiguous',$9) ON CONFLICT DO NOTHING", [accountId, networkId, network.chainId, parsed.operation.nonce.toString(), parsed.hash, parsed.asset.contract, body.raw, payment, address]);
      });
      let result;
      try { result = await gateway.broadcast(networkId, descriptor, body); } catch { return { hash: parsed.hash, status: 'ambiguous' }; }
      await db.query('UPDATE evm_transfers SET status=$3,updated_at=clock_timestamp() WHERE account_id=$1 AND hash=$2', [accountId, parsed.hash, result.status]);
      return result;
    },
  };
}
