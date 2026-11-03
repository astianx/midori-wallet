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
  for (const flag of ['MIDORI_TRANSACTIONS_ENABLED','MIDORI_SPONSORSHIP_ENABLED']) if (env[flag] && !['true','false'].includes(env[flag])) throw new Error(`Invalid ${flag}`);
  const publicOrigin = env.MIDORI_PUBLIC_ORIGIN;
  if (!publicOrigin) throw new Error('MIDORI_PUBLIC_ORIGIN is required');
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
  for (const value of (env.MIDORI_ALLOWED_ORIGINS || '').split(',').filter(Boolean)) {
    const allowed = new URL(value);
    if (allowed.origin !== value || !['https:', 'http:'].includes(allowed.protocol) || (allowed.protocol === 'http:' && !['localhost', '127.0.0.1'].includes(allowed.hostname))) throw new Error('Invalid MIDORI_ALLOWED_ORIGINS');
    allowedOrigins.add(value);
  }
  for (const value of (env.MIDORI_NATIVE_ORIGINS || '').split(',').filter(Boolean)) {
    if (!['https://localhost', 'app://localhost'].includes(value)) throw new Error('Invalid MIDORI_NATIVE_ORIGINS');
    allowedOrigins.add(value);
  }
  if (env.MIDORI_INDEX_ENABLED && !['true','false'].includes(env.MIDORI_INDEX_ENABLED)) throw new Error('Invalid MIDORI_INDEX_ENABLED');
  const paymentUrl = (value, name) => {
    if (!value) return null;
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password || url.hash) throw new Error(`Invalid ${name}`);
    return url.href;
  };
  const paymentsEnabled = env.MIDORI_TRANSACTIONS_ENABLED === 'true';
  const sponsorshipEnabled = env.MIDORI_SPONSORSHIP_ENABLED === 'true';
  const bundlerUrl = paymentUrl(env.MIDORI_BUNDLER_URL, 'MIDORI_BUNDLER_URL');
  const paymasterUrl = paymentUrl(env.MIDORI_PAYMASTER_URL, 'MIDORI_PAYMASTER_URL');
  const paymasterAddress = env.MIDORI_PAYMASTER_ADDRESS?.toLowerCase() || null;
  if (paymasterAddress && !/^0x[0-9a-f]{40}$/.test(paymasterAddress)) throw new Error('Invalid MIDORI_PAYMASTER_ADDRESS');
  const gasToken = '0xfde4c96c8593536e31f229ea8f37b2ada2699bb2';
  const positiveAtomic = (value, fallback, name) => { const text=value || fallback; if(!/^[1-9][0-9]{0,77}$/.test(text) || BigInt(text)>=2n**256n)throw new Error(`Invalid ${name}`);return text; };
  const maxGasFeeAtomic = positiveAtomic(env.MIDORI_MAX_GAS_USDT_ATOMIC, '1000000', 'gas fee cap');
  const sponsorDailyBudget = positiveAtomic(env.MIDORI_SPONSOR_DAILY_BUDGET_WEI, '1000000000000000', 'sponsor budget');
  if (paymentsEnabled && (!bundlerUrl || !paymasterUrl || !paymasterAddress)) throw new Error('Configure bundler, paymaster URL and paymaster address before enabling payments');
  if (sponsorshipEnabled && (!paymentsEnabled || !env.MIDORI_SPONSOR_DAILY_BUDGET_WEI)) throw new Error('Sponsorship requires enabled payments and an explicit daily budget');
  const paymasterProvider = env.MIDORI_PAYMASTER_PROVIDER || 'candide';
  if (!['candide','pimlico'].includes(paymasterProvider)) throw new Error('Invalid paymaster provider');
  const headers = (value) => { const parsed=JSON.parse(value || '{}');if(!parsed || Array.isArray(parsed) || Object.values(parsed).some(item=>typeof item!=='string')) throw new Error('Invalid provider headers');return Object.freeze(parsed); };
  return Object.freeze({
    connectionString, publicOrigin, rpID, host, rpcUrl,
    indexRpcUrl: paymentUrl(env.MIDORI_INDEX_RPC_URL, 'MIDORI_INDEX_RPC_URL') || rpcUrl,
    paymentsEnabled, sponsorshipEnabled, bundlerUrl, paymasterUrl, paymasterAddress, gasToken, maxGasFeeAtomic, sponsorDailyBudget, paymasterProvider,
    entryPoint: '0x0000000071727de22e5e9d8baf0edac6f37da032',
    gasRecipients: Object.freeze([paymasterAddress, ...(env.MIDORI_GAS_RECIPIENTS || '').split(',').filter(Boolean)].filter(Boolean).map(value=>{if(!/^0x[0-9a-fA-F]{40}$/.test(value))throw new Error('Invalid gas recipient');return value.toLowerCase();})),
    bundlerHeaders: headers(env.MIDORI_BUNDLER_HEADERS), paymasterHeaders: headers(env.MIDORI_PAYMASTER_HEADERS),
    sponsorshipPolicyId: env.MIDORI_SPONSORSHIP_POLICY_ID || undefined,
    paymentPollMs: integer(env.MIDORI_PAYMENT_POLL_MS, 3000, 1000, 60000, 'payment polling interval'),
    paymentQuoteSeconds: integer(env.MIDORI_PAYMENT_QUOTE_SECONDS, 120, 30, 300, 'payment quote lifetime'),
    paymentReconcileRange: integer(env.MIDORI_PAYMENT_RECONCILE_RANGE, 2000, 10, 10000, 'reconciliation range'),
    allowedOrigins: Object.freeze([...allowedOrigins]),
    port,
    poolSize: integer(env.MIDORI_DB_POOL_SIZE, 10, 1, 50, 'pool size'),
    challengeSeconds: integer(env.MIDORI_CHALLENGE_SECONDS, 120, 30, 300, 'challenge lifetime'),
    sessionSeconds: integer(env.MIDORI_SESSION_SECONDS, 86400, 60, 604800, 'session lifetime'),
    rateLimit: integer(env.MIDORI_RATE_LIMIT, 60, 5, 10000, 'rate limit'),
    maxDevices: integer(env.MIDORI_MAX_DEVICES, 100, 1, 100, 'device limit'),
    maxAuthenticators: 10,
    maxBlobBytes: 1048576,
    indexEnabled: env.MIDORI_INDEX_ENABLED === 'true',
    indexStartBlock: integer(env.MIDORI_INDEX_START_BLOCK, 0, 0, Number.MAX_SAFE_INTEGER, 'index start block'),
    indexRecentBlocks: integer(env.MIDORI_INDEX_RECENT_BLOCKS, 100, 1, 10000, 'recent blocks'),
    indexBlockBatch: integer(env.MIDORI_INDEX_BLOCK_BATCH, 1000, 1, 50000, 'index batch'),
    indexBackfillBatches: integer(env.MIDORI_INDEX_BACKFILL_BATCHES, 1, 1, 20, 'backfill batches'),
    indexAccountBatch: integer(env.MIDORI_INDEX_ACCOUNT_BATCH, 10, 1, 100, 'account batch'),
    indexConfirmations: integer(env.MIDORI_INDEX_CONFIRMATIONS, 12, 1, 10000, 'confirmations'),
    workerIntervalMs: integer(env.MIDORI_WORKER_INTERVAL_MS, 60000, 1000, 3600000, 'worker interval'),
  });
}
