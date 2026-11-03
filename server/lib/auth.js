import { randomUUID } from 'node:crypto';
import { unauthorized, ApiError, requireValue } from './errors.js';
import { sha256, decodeBase64, verifyEd25519, requestMessage } from './crypto.js';
import { activePrincipal, limitAttempts, lockWallet } from './repositories.js';
import { checkDeviceLimit, deviceEnrollmentMessage } from './wallets.js';

export async function authenticate(db, req) {
  const authorization = req.get('authorization') || '';
  if (!/^Bearer [A-Za-z0-9_-]{43}$/.test(authorization)) throw unauthorized();
  const token = authorization.slice(7);
  const timestamp = req.get('x-midori-timestamp');
  const nonce = req.get('x-midori-nonce');
  const signature = req.get('x-midori-signature');
  if (!/^\d{10}$/.test(timestamp || '') || Math.abs(Date.now() / 1000 - Number(timestamp)) > 60 || !/^[A-Za-z0-9_-]{22,86}$/.test(nonce || '')) throw unauthorized();
  return db.transaction(async client => {
    const { rows } = await client.query(`SELECT s.id AS session_id,s.device_id,s.wallet_id,d.public_key FROM sessions s JOIN devices d ON d.id=s.device_id
      WHERE s.token_hash=$1 AND s.expires_at>clock_timestamp() AND s.revoked_at IS NULL AND d.revoked_at IS NULL`, [sha256(token)]);
    const principal = rows[0];
    if (!principal) throw unauthorized();
    const message = requestMessage({ method: req.method, target: req.originalUrl, timestamp, nonce, token, body: req.rawBody || '' });
    if (!verifyEd25519(principal.public_key, message, signature)) throw unauthorized();
    await activePrincipal(client, principal);
    const consumed = await client.query(`INSERT INTO request_nonces(device_id,nonce,fingerprint,expires_at) VALUES ($1,$2,$3,clock_timestamp()+interval '3 minutes') ON CONFLICT DO NOTHING RETURNING nonce`, [principal.device_id, nonce, sha256(message)]);
    if (!consumed.rowCount) throw new ApiError(409, 'REPLAY_DETECTED', 'Request nonce has already been used');
    return principal;
  });
}

export async function enrollDevice(client, config, principal, body) {
  const { exactKeys } = await import('./errors.js');
  exactKeys(body, ['publicKey', 'signature']);
  decodeBase64(body.publicKey, 32);
  requireValue(verifyEd25519(body.publicKey, deviceEnrollmentMessage(principal.wallet_id, body.publicKey), body.signature), 'New device must prove possession of its key');
  await lockWallet(client, principal.wallet_id);
  await checkDeviceLimit(client, principal.wallet_id, config.maxDevices);
  const id = randomUUID();
  await client.query('INSERT INTO devices(id,wallet_id,public_key) VALUES ($1,$2,$3)', [id, principal.wallet_id, body.publicKey]);
  return { id, publicKey: body.publicKey };
}

export async function revokeDevice(client, principal, deviceId) {
  const result = await client.query('UPDATE devices SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE id=$1 AND wallet_id=$2 RETURNING id', [deviceId, principal.wallet_id]);
  if (!result.rowCount) throw new ApiError(404, 'NOT_FOUND', 'Device not found');
  await client.query('UPDATE sessions SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE device_id=$1', [deviceId]);
  await client.query('UPDATE authenticators SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE device_id=$1', [deviceId]);
}

export async function limitUnauthenticated(db, config, req) {
  await limitAttempts(db, `ip:${sha256(req.socket.remoteAddress || 'unknown')}`, config.rateLimit);
}
