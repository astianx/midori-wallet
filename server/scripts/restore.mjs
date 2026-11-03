import { pathToFileURL } from 'node:url';
import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { checkSchema } from '../lib/migrations.js';
import { runPg } from './backup.mjs';
import { grantRuntime } from './bootstrap.mjs';

export async function restore(connectionString, filename, runtimeRole) {
  const db = createDatabase(loadConfig({ DATABASE_URL: connectionString }));
  try {
    const existing = await db.query("SELECT 1 FROM pg_namespace WHERE nspname='midori'");
    if (existing.rowCount) throw new Error('Restore requires an empty database without the midori schema');
    await runPg('pg_restore', ['--single-transaction', '--exit-on-error', '--no-owner', '--no-acl', '--dbname', new URL(connectionString).pathname.slice(1), filename], connectionString);
    if (!await checkSchema(db)) throw new Error('Restored migration history does not match this service version');
    if (runtimeRole) await grantRuntime(db, runtimeRole);
    return { restored: true, schema: 'midori', migrationHistoryVerified: true };
  } finally { await db.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (!process.argv[2]) throw new Error('Usage: MIDORI_RESTORE_DATABASE_URL=... npm run db:restore -- backup.dump');
    if (!process.env.MIDORI_RESTORE_DATABASE_URL) throw new Error('MIDORI_RESTORE_DATABASE_URL must explicitly select an empty destination');
    console.log(JSON.stringify(await restore(process.env.MIDORI_RESTORE_DATABASE_URL, process.argv[2], process.env.MIDORI_RESTORE_RUNTIME_ROLE)));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
