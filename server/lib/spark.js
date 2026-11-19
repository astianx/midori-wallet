import { getAddress, verifyMessage } from 'ethers';
import { sparkTools, validateBitcoinAddress } from '../../packages/wallet-core/src/index.js';
import { exactKeys, requireValue } from './errors.js';
import { account } from './repositories.js';

export async function sparkAccount(db, walletId, accountId) {
  return (await db.query('SELECT address,deposit_address FROM spark_accounts WHERE wallet_id=$1 AND account_id=$2', [walletId, accountId])).rows[0];
}
export async function registerSpark(db, principal, accountId, body) {
  exactKeys(body, ['address','depositAddress','signature','ownerSignature']);
  const { validateSparkAddress, sparkBindingMessage, sparkReader } = await sparkTools();
  requireValue(validateSparkAddress(body.address) && validateBitcoinAddress(body.depositAddress), 'Invalid Spark account');
  requireValue(typeof body.signature === 'string' && /^(?:[a-f0-9]{2}){64,72}$/i.test(body.signature) && typeof body.ownerSignature === 'string' && /^0x[a-f0-9]{130}$/i.test(body.ownerSignature), 'Invalid Spark proof');
  const saved = await account(db, principal.wallet_id, accountId), reader = sparkReader(body.address);
  const message = sparkBindingMessage(principal.wallet_id, accountId, body.address, body.depositAddress);
  let valid = false;
  try { valid = getAddress(verifyMessage(message, body.ownerSignature)) === getAddress(saved.descriptor.ownerAddress) && await reader.verify(message, body.signature); } catch { /* Invalid proofs fail closed. */ }
  requireValue(valid, 'Invalid Spark proof');
  const previous = await sparkAccount(db, principal.wallet_id, accountId);
  if (previous) { requireValue(previous.address === body.address && previous.deposit_address === body.depositAddress, 'Spark account changed'); return { address: previous.address, depositAddress: previous.deposit_address, network: 'spark' }; }
  const deposits = await reader.getStaticDepositAddresses();
  requireValue(deposits.some(item => item.depositAddress === body.depositAddress), 'The Bitcoin deposit address must belong to this Spark account');
  await db.query('INSERT INTO spark_accounts(wallet_id,account_id,address,deposit_address) VALUES($1,$2,$3,$4) ON CONFLICT(account_id) DO NOTHING', [principal.wallet_id, accountId, body.address, body.depositAddress]);
  const result = await sparkAccount(db, principal.wallet_id, accountId);
  requireValue(result.address === body.address && result.deposit_address === body.depositAddress, 'Spark account changed');
  return { address: result.address, depositAddress: result.deposit_address, network: 'spark' };
}
