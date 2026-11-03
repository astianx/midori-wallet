import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { loadConfig } from './lib/config.js';
import { createDatabase } from './lib/db.js';

export async function maintainExpirations(db) {
  return db.transaction(async client => {
    const counts = {};
    for (const table of ['auth_challenges', 'request_nonces', 'auth_attempts', 'sessions']) {
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
      try { console.log(JSON.stringify({ event: 'expiration_cleanup', removed: await maintainExpirations(db) })); }
      catch { console.error(JSON.stringify({ event: 'expiration_cleanup_failed', code: 'DATABASE_UNAVAILABLE' })); if (process.argv.includes('--once')) process.exitCode = 1; }
      if (process.argv.includes('--once')) break;
      try { await delay(config.workerIntervalMs, undefined, { signal: controller.signal }); } catch { break; }
    } while (!controller.signal.aborted);
  } finally { await db.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runWorker().catch(() => { console.error('{"event":"worker_failed"}'); process.exitCode = 1; });
}
