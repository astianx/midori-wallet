function integer(value, fallback, min, max, name) {
  const number = Number(value ?? fallback);
  if (!Number.isSafeInteger(number) || number < min || number > max) throw new Error(`Invalid ${name}`);
  return number;
}

export function loadConfig(env = process.env) {
  const connectionString = env.DATABASE_URL;
  let database;
  try { database = new URL(connectionString); } catch { throw new Error('DATABASE_URL is required'); }
  if (!['postgres:', 'postgresql:'].includes(database.protocol) || !database.hostname || database.pathname.length < 2) throw new Error('Invalid DATABASE_URL');
  if (database.searchParams.get('sslmode') === 'no-verify') throw new Error('Database TLS certificate verification is required');
  if (env.MIDORI_CHAIN_ID && env.MIDORI_CHAIN_ID !== '8453') throw new Error('F2 requires Base mainnet chain 8453');
  if (env.MIDORI_NETWORK && env.MIDORI_NETWORK !== 'base') throw new Error('F2 requires MIDORI_NETWORK=base');
  for (const flag of ['MIDORI_TRANSACTIONS_ENABLED', 'MIDORI_SPONSORSHIP_ENABLED']) {
    if (env[flag] && env[flag] !== 'false') throw new Error(`${flag} is not implemented by F2`);
  }
  const publicOrigin = env.MIDORI_PUBLIC_ORIGIN || 'http://localhost:5173';
  let origin;
  try { origin = new URL(publicOrigin); } catch { throw new Error('Invalid MIDORI_PUBLIC_ORIGIN'); }
  if (origin.origin !== publicOrigin || origin.username || origin.password || (origin.protocol !== 'https:' && !(origin.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(origin.hostname)))) throw new Error('MIDORI_PUBLIC_ORIGIN must be an HTTPS origin or local HTTP origin');
  const rpID = env.MIDORI_RP_ID || origin.hostname;
  if (rpID !== origin.hostname && !origin.hostname.endsWith(`.${rpID}`)) throw new Error('RP ID must match the public origin');
  const host = env.MIDORI_API_HOST || '127.0.0.1';
  if (!['127.0.0.1', '::1', '0.0.0.0'].includes(host)) throw new Error('Invalid MIDORI_API_HOST');
  const rpcUrl = env.MIDORI_RPC_URL || 'https://mainnet.base.org';
  try { if (new URL(rpcUrl).protocol !== 'https:') throw new Error(); } catch { throw new Error('Base RPC must use HTTPS'); }
  const port = integer(env.MIDORI_API_PORT || env.PORT, 8080, 1024, 65535, 'API port');
  const allowedOrigins = new Set([publicOrigin]);
  if (origin.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(origin.hostname)) {
    const alternate = new URL(publicOrigin); alternate.hostname = origin.hostname === 'localhost' ? '127.0.0.1' : 'localhost';
    for (const value of [alternate.origin, `http://127.0.0.1:${port}`, `http://localhost:${port}`]) allowedOrigins.add(value);
  }
  return Object.freeze({
    connectionString, publicOrigin, rpID, host, rpcUrl,
    allowedOrigins: Object.freeze([...allowedOrigins]),
    port,
    poolSize: integer(env.MIDORI_DB_POOL_SIZE, 10, 1, 50, 'pool size'),
    challengeSeconds: integer(env.MIDORI_CHALLENGE_SECONDS, 120, 30, 300, 'challenge lifetime'),
    sessionSeconds: integer(env.MIDORI_SESSION_SECONDS, 86400, 60, 604800, 'session lifetime'),
    rateLimit: integer(env.MIDORI_RATE_LIMIT, 60, 5, 10000, 'rate limit'),
    maxDevices: integer(env.MIDORI_MAX_DEVICES, 100, 1, 100, 'device limit'),
    maxAuthenticators: 10,
    maxBlobBytes: 1048576,
    workerIntervalMs: integer(env.MIDORI_WORKER_INTERVAL_MS, 60000, 1000, 3600000, 'worker interval'),
  });
}
