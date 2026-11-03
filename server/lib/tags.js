import { randomUUID, randomBytes } from 'node:crypto';
import { getAddress, verifyMessage } from 'ethers';
import { ApiError, exactKeys, requireValue, notFound } from './errors.js';
import { account, lockWallet, limitAttempts } from './repositories.js';
import { uuidPattern } from './crypto.js';

const reserved = new Set(['admin', 'administrator', 'midori', 'midori-wallet', 'support', 'help', 'official', 'astian', 'system', 'wallet']);

export function normalizeTag(value) {
  requireValue(typeof value === 'string' && value.length <= 65, 'Invalid tag');
  const alias = value.trim().replace(/^@/, '').toLowerCase();
  requireValue(/^[a-z0-9][a-z0-9-]{0,62}$/.test(alias), 'Invalid tag');
  requireValue(!reserved.has(alias), 'This tag is reserved');
  return alias;
}

export async function ownTag(db, walletId) {
  const { rows } = await db.query('SELECT t.alias,t.revision::text,a.chain_id,a.address FROM wallet_tags t JOIN chain_accounts a ON a.id=t.account_id WHERE t.wallet_id=$1 AND t.active', [walletId]);
  return { tag: rows.length ? { alias: rows[0].alias, revision: rows[0].revision, chainId: rows[0].chain_id, address: getAddress(rows[0].address) } : null };
}

export async function tagOptions(client, config, principal, body) {
  exactKeys(body, ['alias', 'accountId']);
  requireValue(uuidPattern.test(body.accountId), 'Invalid account');
  await limitAttempts(client, `tag-register:${principal.wallet_id}`, 10);
  const alias = normalizeTag(body.alias);
  const saved = await account(client, principal.wallet_id, body.accountId);
  const id = randomUUID();
  const expiresAt = new Date(Date.now() + config.challengeSeconds * 1000).toISOString();
  const message = `Midori tag v1\n${config.publicOrigin}\n${id}\n${randomBytes(32).toString('hex')}\n${principal.wallet_id}\n${body.accountId}\n8453\n${saved.address}\n@${alias}\n${expiresAt}`;
  await client.query('INSERT INTO tag_challenges(id,wallet_id,account_id,alias,message,expires_at) VALUES ($1,$2,$3,$4,$5,$6)', [id, principal.wallet_id, body.accountId, alias, message, expiresAt]);
  return { id, message, expiresAt, alias, chainId: 8453, address: saved.address };
}

export async function registerTag(client, principal, body) {
  exactKeys(body, ['id', 'signature']);
  requireValue(uuidPattern.test(body.id) && typeof body.signature === 'string' && /^0x[0-9a-fA-F]{130}$/.test(body.signature), 'Invalid tag proof');
  await lockWallet(client, principal.wallet_id);
  const { rows } = await client.query('SELECT c.*,a.owner_address,a.address FROM tag_challenges c JOIN chain_accounts a ON a.id=c.account_id AND a.wallet_id=c.wallet_id WHERE c.id=$1 AND c.wallet_id=$2 AND c.consumed_at IS NULL AND c.expires_at>clock_timestamp() FOR UPDATE OF c', [body.id, principal.wallet_id]);
  const proof = rows[0];
  if (!proof) throw new ApiError(409, 'TAG_PROOF_EXPIRED', 'Request a new tag confirmation');
  let signer;
  try { signer = verifyMessage(proof.message, body.signature).toLowerCase(); } catch { /* invalid proof is rejected below */ }
  requireValue(signer === proof.owner_address, 'The signature does not control this account');
  const alias = normalizeTag(proof.alias);
  await client.query('INSERT INTO tag_reservations(alias,wallet_id) VALUES ($1,$2) ON CONFLICT DO NOTHING', [alias, principal.wallet_id]);
  const reservation = await client.query('SELECT wallet_id FROM tag_reservations WHERE alias=$1 FOR UPDATE', [alias]);
  if (reservation.rows[0].wallet_id !== principal.wallet_id) throw new ApiError(409, 'TAG_TAKEN', 'Username already taken');
  await client.query(`INSERT INTO wallet_tags(alias,wallet_id,account_id) VALUES ($1,$2,$3)
    ON CONFLICT (wallet_id) DO UPDATE SET alias=EXCLUDED.alias,account_id=EXCLUDED.account_id,
    revision=wallet_tags.revision+1,active=true,updated_at=clock_timestamp()`, [alias, principal.wallet_id, proof.account_id]);
  await client.query('UPDATE tag_challenges SET consumed_at=clock_timestamp() WHERE id=$1', [body.id]);
  return ownTag(client, principal.wallet_id);
}

export async function resolveTag(client, config, principal, body) {
  exactKeys(body, ['alias', 'chainId']);
  await limitAttempts(client, `tag-resolve:${principal.wallet_id}`, config.rateLimit);
  requireValue(body.chainId === 8453, 'Tags must be resolved on Base');
  const alias = normalizeTag(body.alias);
  const { rows } = await client.query('SELECT t.revision::text,a.address FROM wallet_tags t JOIN chain_accounts a ON a.id=t.account_id WHERE t.alias=$1 AND t.active', [alias]);
  if (!rows.length) throw notFound();
  const result = { id: randomUUID(), alias, chainId: 8453, address: getAddress(rows[0].address), revision: rows[0].revision, expiresAt: new Date(Date.now() + 120000).toISOString() };
  await client.query('INSERT INTO tag_resolutions(id,wallet_id,alias,revision,chain_id,address,expires_at) VALUES ($1,$2,$3,$4,8453,$5,$6)', [result.id, principal.wallet_id, alias, result.revision, result.address.toLowerCase(), result.expiresAt]);
  return result;
}

export async function validateResolution(client, walletId, id, destination) {
  requireValue(uuidPattern.test(id), 'Invalid tag resolution');
  const { rows } = await client.query(`SELECT r.address FROM tag_resolutions r JOIN wallet_tags t ON t.alias=r.alias AND t.revision=r.revision AND t.active
    JOIN chain_accounts a ON a.id=t.account_id AND a.chain_id=r.chain_id AND a.address=r.address
    WHERE r.id=$1 AND r.wallet_id=$2 AND r.expires_at>clock_timestamp()`, [id, walletId]);
  if (!rows.length || rows[0].address !== destination.toLowerCase()) throw new ApiError(409, 'TAG_RECONFIRM_REQUIRED', 'Resolve the tag again and confirm its current destination');
}
