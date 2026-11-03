import { readFile } from 'node:fs/promises';
import { parseEnv } from 'node:util';
import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { migrate } from '../lib/migrations.js';
import { grantRuntime } from './bootstrap.mjs';

let local = {};
try { local = parseEnv(await readFile(new URL('../.env', import.meta.url), 'utf8')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
const env = { ...local, ...process.env };
const config = loadConfig({ ...env, DATABASE_URL: env.MIDORI_MIGRATION_DATABASE_URL || env.DATABASE_URL });
const runtimeRole = env.MIDORI_DB_RUNTIME_ROLE || (local.DATABASE_URL && decodeURIComponent(new URL(local.DATABASE_URL).username));
const db = createDatabase(config);
try {
  const applied = await migrate(db);
  if (runtimeRole) await grantRuntime(db, runtimeRole);
  console.log(JSON.stringify({ applied, runtimeGrants: !!runtimeRole }));
} catch { console.error('Migration failed; check migration history, ownership and database availability'); process.exitCode = 1; }
finally { await db.close(); }
