import express from 'express';
import { randomUUID } from 'node:crypto';
import { ApiError, exactKeys, notFound, requireValue } from './lib/errors.js';
import { uuidPattern } from './lib/crypto.js';
import { checkSchema } from './lib/migrations.js';
import { authenticate, enrollDevice, limitUnauthenticated, revokeDevice } from './lib/auth.js';
import { account, activePrincipal, catalog } from './lib/repositories.js';
import { walletService } from './lib/wallets.js';
import { webauthnService } from './lib/webauthn.js';
import { readBlob, writeBlob } from './lib/storage.js';
import { readBalances } from './lib/balances.js';

const route = action => (req, res, next) => Promise.resolve(action(req, res)).catch(next);
const uuid = value => requireValue(uuidPattern.test(value), 'Invalid resource ID');

export function createApp({ db, config, compatibilityApp, logger = () => {} }) {
  const app = express();
  const wallets = walletService(db, config);
  const webauthn = webauthnService(db, config);
  app.disable('x-powered-by');
  app.set('query parser', 'simple');
  app.use((req, res, next) => {
    req.requestId = randomUUID();
    res.set({ 'X-Request-ID': req.requestId, 'X-Content-Type-Options': 'nosniff', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' });
    const origin = req.get('origin');
    if (origin && !config.allowedOrigins.includes(origin)) return next(new ApiError(403, 'ORIGIN_DENIED', 'Origin not allowed'));
    if (origin) res.set({ 'Access-Control-Allow-Origin': origin, Vary: 'Origin', 'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-Midori-Timestamp,X-Midori-Nonce,X-Midori-Signature', 'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS', 'Access-Control-Expose-Headers': 'X-Request-ID' });
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });
  app.use(express.json({ limit: '1450kb', strict: true, verify(req, res, buffer) { req.rawBody = buffer.toString('utf8'); } }));
  app.get('/healthz', (req, res) => res.json({ status: 'ok', service: 'midori-api', apiVersion: 'v1' }));
  app.get('/readyz', route(async (req, res) => {
    let ready = false;
    try { ready = await checkSchema(db); } catch { /* readiness exposes no connection details */ }
    res.status(ready ? 200 : 503).json({ status: ready ? 'ready' : 'unavailable', database: ready ? 'ready' : 'unavailable' });
  }));
  app.get('/api/v1/openapi.json', (req, res) => res.sendFile(new URL('./openapi.json', import.meta.url).pathname));
  app.get('/api/v1/config', (req, res) => res.json({ apiVersion: 'v1', network: { name: 'base', chainId: 8453, testnet: false }, account: { schema: 'midori.safe-account', version: 1, type: 'safe-erc4337' }, capabilities: { registration: true, devices: true, webauthn: true, encryptedBlobs: true, payments: false, signing: 'client', optionalProviders: false }, gas: { midoriFeeAtomic: '0', preferredMode: 'token', tokenSymbol: 'USDT', enabled: false }, limits: { maxDevices: config.maxDevices, maxAuthenticators: config.maxAuthenticators, maxBlobBytes: config.maxBlobBytes }, authentication: { rpID: config.rpID, origin: config.publicOrigin, signedRequests: 'ed25519-v1', sessionLifetimeSeconds: config.sessionSeconds } }));
  app.get('/api/v1/assets', route(async (req, res) => res.json({ assets: await catalog(db) })));

  const publicPost = (path, action, status = 200) => app.post(path, route(async (req, res) => { await limitUnauthenticated(db, config, req); res.status(status).json(await action(req.body)); }));
  publicPost('/api/v1/registration/options', body => wallets.registrationOptions(body));
  publicPost('/api/v1/registration/verify', body => wallets.register(body), 201);
  publicPost('/api/v1/auth/options', body => wallets.loginOptions(body));
  publicPost('/api/v1/auth/verify', body => wallets.login(body));
  publicPost('/api/v1/recovery/options', body => wallets.recoveryOptions(body));
  publicPost('/api/v1/recovery/verify', body => wallets.recover(body), 201);
  publicPost('/api/v1/auth/webauthn/options', body => webauthn.loginOptions(body));
  publicPost('/api/v1/auth/webauthn/verify', body => webauthn.login(body));

  const protectedRoute = (method, path, action, status = 200, transaction = true) => app[method](path, route(async (req, res) => {
    const principal = await authenticate(db, req);
    const value = transaction ? await db.transaction(async client => { await activePrincipal(client, principal); return action(req, client, principal); }) : await action(req, db, principal);
    res.status(status).json(value);
  }));
  protectedRoute('get', '/api/v1/me', async (req, client, principal) => {
    const { rows } = await client.query('SELECT id FROM chain_accounts WHERE wallet_id=$1 ORDER BY account_index', [principal.wallet_id]);
    return { walletId: principal.wallet_id, deviceId: principal.device_id, accounts: await Promise.all(rows.map(row => account(client, principal.wallet_id, row.id))) };
  });
  protectedRoute('get', '/api/v1/accounts/:id', async (req, client, principal) => { uuid(req.params.id); return account(client, principal.wallet_id, req.params.id); });
  protectedRoute('get', '/api/v1/accounts/:id/balances', async (req, client, principal) => { uuid(req.params.id); return readBalances(db, config, principal.wallet_id, req.params.id); }, 200, false);
  protectedRoute('get', '/api/v1/devices', async (req, client, principal) => {
    const { rows } = await client.query('SELECT id,public_key,created_at,revoked_at FROM devices WHERE wallet_id=$1 ORDER BY created_at', [principal.wallet_id]);
    return { devices: rows.map(row => ({ id: row.id, publicKey: row.public_key, createdAt: row.created_at, revokedAt: row.revoked_at })) };
  });
  protectedRoute('post', '/api/v1/devices', (req, client, principal) => enrollDevice(client, config, principal, req.body), 201);
  protectedRoute('delete', '/api/v1/devices/:id', async (req, client, principal) => { uuid(req.params.id); await revokeDevice(client, principal, req.params.id); return { revoked: true }; });
  protectedRoute('post', '/api/v1/auth/logout', async (req, client, principal) => { await client.query('UPDATE sessions SET revoked_at=clock_timestamp() WHERE id=$1', [principal.session_id]); return { revoked: true }; });
  protectedRoute('get', '/api/v1/blobs/:namespace', (req, client, principal) => readBlob(client, principal.wallet_id, req.params.namespace));
  protectedRoute('put', '/api/v1/blobs/:namespace', (req, client, principal) => writeBlob(client, principal.wallet_id, req.params.namespace, req.body));
  protectedRoute('delete', '/api/v1/blobs/:namespace', (req, client, principal) => writeBlob(client, principal.wallet_id, req.params.namespace, req.body, true));
  protectedRoute('post', '/api/v1/authenticators/options', (req, client, principal) => { exactKeys(req.body, []); return webauthn.registrationOptions(client, principal); });
  protectedRoute('post', '/api/v1/authenticators/verify', (req, client, principal) => webauthn.register(principal, req.body), 201, false);
  protectedRoute('get', '/api/v1/authenticators', async (req, client, principal) => {
    const { rows } = await client.query('SELECT credential_id,device_id,device_type,backed_up,created_at,revoked_at FROM authenticators WHERE wallet_id=$1 ORDER BY created_at', [principal.wallet_id]);
    return { authenticators: rows.map(row => ({ credentialId: row.credential_id, deviceId: row.device_id, deviceType: row.device_type, backedUp: row.backed_up, createdAt: row.created_at, revokedAt: row.revoked_at })) };
  });
  protectedRoute('delete', '/api/v1/authenticators/:id', async (req, client, principal) => {
    requireValue(req.params.id.length <= 2048);
    const result = await client.query('UPDATE authenticators SET revoked_at=COALESCE(revoked_at,clock_timestamp()) WHERE credential_id=$1 AND wallet_id=$2 RETURNING credential_id', [req.params.id, principal.wallet_id]);
    if (!result.rowCount) throw notFound();
    return { revoked: true };
  });
  if (compatibilityApp) app.use((req, res, next) => req.path.startsWith('/api/') && !req.path.startsWith('/api/f1/') ? next() : compatibilityApp(req, res, next));
  app.use((req, res, next) => next(notFound()));
  app.use((error, req, res, next) => {
    if (res.headersSent) return next(error);
    let status = error instanceof ApiError ? error.status : 500;
    let code = error instanceof ApiError ? error.code : 'INTERNAL_ERROR';
    let message = error instanceof ApiError ? error.message : 'Request could not be completed';
    if (error.code === '23505') { status = 409; code = 'DUPLICATE_RESOURCE'; message = 'Identity, device or account already registered'; }
    if (['40P01', '40001'].includes(error.code)) { status = 409; code = 'RETRY_REQUIRED'; message = 'Concurrent change; retry the request with a new nonce'; }
    if (['23514', '23503', '22P02', '22003'].includes(error.code)) { status = 400; code = 'INVALID_REQUEST'; message = 'Data violates a persistence constraint'; }
    if (error.type === 'entity.parse.failed' || error.type === 'entity.too.large') { status = error.status; code = 'INVALID_BODY'; message = 'Invalid or oversized JSON body'; }
    if (/^(08|53|57P)/.test(error.code || '') || ['55000', '3D000', '28P01', '28000', 'ECONNREFUSED', 'ETIMEDOUT', 'ENOTFOUND', 'ECONNRESET'].includes(error.code) || /connection timeout|timeout exceeded|pool.*end/i.test(error.message)) { status = 503; code = 'DATABASE_UNAVAILABLE'; message = 'Persistence is temporarily unavailable'; }
    if (status >= 500) logger({ event: 'request_failed', requestId: req.requestId, code });
    if (status === 429) res.set('Retry-After', String(error.details?.retryAfter || 60));
    res.status(status).json({ error: { code, message, requestId: req.requestId, ...(error instanceof ApiError && error.details ? { details: error.details } : {}) } });
  });
  return app;
}
