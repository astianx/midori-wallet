import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { loadConfig } from './lib/config.js';
import { createDatabase } from './lib/db.js';
import { expireQuotes, deliverPayments, reconcilePayments } from './lib/payments.js';
import { synchronizeHistory } from './lib/indexer.js';

export async function maintainExpirations(db) {
  return db.transaction(async client => {
    const counts = {};
    for (const table of ['auth_challenges', 'request_nonces', 'auth_attempts', 'sessions', 'tag_challenges']) {
      const result = await client.query(`DELETE FROM ${table} WHERE ctid IN (SELECT ctid FROM ${table} WHERE expires_at<=clock_timestamp() ORDER BY expires_at LIMIT 1000 FOR UPDATE SKIP LOCKED)`);
      counts[table] = result.rowCount;
    }
    return counts;
  });
}

export async function runWorker(config = loadConfig()) {
  const db = createDatabase(config, entry => console.error(JSON.stringify(entry)));
  const controller = new AbortController();
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => controller.abort());
  const provider = config.paymentsEnabled ? (await import('./lib/payment-provider.js')).paymentProvider(config) : undefined;
  const once = process.argv.includes('--once');
  const loop = async (name, interval, action) => {
    do {
      let nextInterval = interval;
      try { const result = await action(); if (result) console.log(JSON.stringify({ event: name, ...result })); }
      catch (error) {
        if (['CHAIN_RATE_LIMITED','PAYMENT_PROVIDER_LIMITED'].includes(error.code)) nextInterval = Math.max(interval, (error.details?.retryAfter || 60) * 1000);
        console.error(JSON.stringify({ event: `${name}_failed`, code: error.code || 'SERVICE_UNAVAILABLE', retryAfterSeconds: Math.ceil(nextInterval / 1000) }));
        if (once) process.exitCode = 1;
      }
      if (once || controller.signal.aborted) break;
      try { await delay(nextInterval, undefined, { signal: controller.signal }); } catch { break; }
    } while (!controller.signal.aborted);
  };
  try {
    await Promise.all([
      loop('expiration_cleanup', config.workerIntervalMs, async () => ({ removed: await maintainExpirations(db) })),
      loop('history_synchronized', config.workerIntervalMs, () => synchronizeHistory(db, config)),
      loop('payments_reconciled', config.paymentPollMs || 3000, async () => {
        await expireQuotes(db);
        if (!config.paymentsEnabled) return null;
        return { delivered: await deliverPayments(db, config, provider), checked: await reconcilePayments(db, config, provider) };
      }),
    ]);
  } finally { await db.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runWorker().catch(() => { console.error('{"event":"worker_failed"}'); process.exitCode = 1; });
}
