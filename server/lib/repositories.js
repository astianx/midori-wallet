import { randomUUID } from 'node:crypto';
import { getAddress } from 'ethers';
import { ApiError, notFound, requireValue, unauthorized } from './errors.js';
import { randomToken, sha256, challengeMessage, uuidPattern } from './crypto.js';

export async function issueChallenge(db, config, purpose, context, challenge = randomToken()) {
  const id = randomUUID();
  const { rows } = await db.query("INSERT INTO auth_challenges(id,purpose,challenge_hash,context,expires_at) VALUES ($1,$2,$3,$4,clock_timestamp()+make_interval(secs=>$5)) RETURNING expires_at", [id, purpose, sha256(challenge), context, config.challengeSeconds]);
  return { id, purpose, challenge, context, expiresAt: rows[0].expires_at, message: challengeMessage(config.publicOrigin, id, purpose, challenge, context) };
}

export async function consumeChallenge(client, id, purpose, challenge) {
  requireValue(uuidPattern.test(id) && typeof challenge === 'string' && challenge.length < 2048);
  const { rows } = await client.query('UPDATE auth_challenges SET consumed_at=clock_timestamp() WHERE id=$1 AND purpose=$2 AND challenge_hash=$3 AND consumed_at IS NULL AND expires_at>clock_timestamp() RETURNING context', [id, purpose, sha256(challenge)]);
  return rows[0]?.context;
}

export async function createSession(client, config, device) {
  const token = randomToken();
  const id = randomUUID();
  const { rows } = await client.query('INSERT INTO sessions(id,wallet_id,device_id,token_hash,expires_at) VALUES ($1,$2,$3,$4,clock_timestamp()+make_interval(secs=>$5)) RETURNING expires_at', [id, device.wallet_id, device.id, sha256(token), config.sessionSeconds]);
  return { walletId: device.wallet_id, deviceId: device.id, sessionId: id, token, expiresAt: rows[0].expires_at };
}

export async function lockDevice(client, deviceId) {
  const { rows } = await client.query('SELECT * FROM devices WHERE id=$1 AND revoked_at IS NULL FOR UPDATE', [deviceId]);
  return rows[0];
}

export async function lockWallet(client, walletId) {
  await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1::text,9375251))', [walletId]);
}

export async function limitAttempts(db, scope, max, seconds = 60) {
  const { rows } = await db.query(`INSERT INTO auth_attempts(scope,attempts,expires_at) VALUES ($1,1,clock_timestamp()+make_interval(secs=>$2))
    ON CONFLICT (scope) DO UPDATE SET attempts=CASE WHEN auth_attempts.expires_at<=clock_timestamp() THEN 1 ELSE auth_attempts.attempts+1 END,
    expires_at=CASE WHEN auth_attempts.expires_at<=clock_timestamp() THEN EXCLUDED.expires_at ELSE auth_attempts.expires_at END RETURNING attempts`, [scope, seconds]);
  if (rows[0].attempts > max) throw new ApiError(429, 'RATE_LIMITED', 'Too many attempts; retry after the current window', { retryAfter: seconds });
}

export async function activePrincipal(client, principal) {
  const device = await lockDevice(client, principal.device_id);
  const { rows } = await client.query('SELECT id FROM sessions WHERE id=$1 AND device_id=$2 AND revoked_at IS NULL AND expires_at>clock_timestamp()', [principal.session_id, principal.device_id]);
  if (!device || !rows.length) throw unauthorized();
  return device;
}

export async function catalog(db) {
  const { rows } = await db.query('SELECT chain_id,contract,symbol,decimals,representation FROM asset_registry WHERE enabled ORDER BY contract');
  return rows.map(row => ({ chainId: row.chain_id, contract: getAddress(row.contract), symbol: row.symbol, decimals: row.decimals, representation: row.representation }));
}

export async function account(db, walletId, accountId) {
  const { rows } = await db.query('SELECT id,descriptor FROM chain_accounts WHERE id=$1 AND wallet_id=$2', [accountId, walletId]);
  if (!rows.length) throw notFound();
  const { rows: assets } = await db.query(`SELECT ar.contract,ar.symbol,ar.decimals,ar.representation,aa.balance_atomic::text,aa.observed_block::text
    FROM account_assets aa JOIN asset_registry ar USING(chain_id,contract) WHERE aa.account_id=$1 AND ar.enabled ORDER BY ar.contract`, [accountId]);
  return { id: rows[0].id, descriptor: rows[0].descriptor, chainId: 8453, address: rows[0].descriptor.address,
    assets: assets.map(row => ({ contract: getAddress(row.contract), symbol: row.symbol, decimals: row.decimals, representation: row.representation, address: rows[0].descriptor.address, balanceAtomic: row.balance_atomic, observedBlock: row.observed_block })) };
}
