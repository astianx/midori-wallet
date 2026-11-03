import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { loadConfig } from './lib/config.js';
import { createDatabase } from './lib/db.js';
import { synchronizeHistory } from './lib/indexer.js';

export async function maintainExpirations(db) {
  return db.transaction(async client => {
    const counts = {};
    for (const table of ['auth_challenges', 'request_nonces', 'auth_attempts', 'sessions', 'tag_challenges', 'tag_resolutions']) {
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
  try {
    do {
      let nextIntervalMs = config.workerIntervalMs;
      try { console.log(JSON.stringify({ event: 'expiration_cleanup', removed: await maintainExpirations(db) })); }
      catch { console.error(JSON.stringify({ event: 'expiration_cleanup_failed', code: 'DATABASE_UNAVAILABLE' })); if (process.argv.includes('--once')) process.exitCode = 1; }
      try {
        const result = await synchronizeHistory(db, config);
        if (result.enabled) console.log(JSON.stringify({ event: 'history_synchronized', ...result }));
      } catch (error) {
        if (error.code === 'CHAIN_RATE_LIMITED') nextIntervalMs = Math.max(nextIntervalMs, error.details.retryAfter * 1000);
        console.error(JSON.stringify({ event: 'history_sync_failed', code: error.code || 'HISTORY_UNAVAILABLE', retryAfterSeconds: Math.ceil(nextIntervalMs / 1000) }));
        if (process.argv.includes('--once')) process.exitCode = 1;
      }
      if (process.argv.includes('--once')) break;
      try { await delay(nextIntervalMs, undefined, { signal: controller.signal }); } catch { break; }
    } while (!controller.signal.aborted);
  } finally { await db.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runWorker().catch(() => { console.error('{"event":"worker_failed"}'); process.exitCode = 1; });
}
