import pg from 'pg';
import { randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { parseEnv } from 'node:util';
import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { backup } from './backup.mjs';
import { restore } from './restore.mjs';
import { sha256 } from '../lib/crypto.js';

const runtime = parseEnv(await readFile(new URL('../.env', import.meta.url), 'utf8'));
const migration = parseEnv(await readFile(new URL('../.env.migrations', import.meta.url), 'utf8'));
if (runtime.MIDORI_MIGRATION_DATABASE_URL) throw new Error('Owner credentials must not be present in the runtime environment');
const source = createDatabase(loadConfig(runtime));
const admin = new pg.Client({ connectionString: process.env.MIDORI_PG_ADMIN_URL }); await admin.connect();
const destination = `midori_live_restore_${randomBytes(6).toString('hex')}`;
const sourceUrl = new URL(migration.DATABASE_URL);
const runtimeRole = new URL(runtime.DATABASE_URL).username;
const ownerRole = sourceUrl.username;
for (const name of [runtimeRole, ownerRole]) if (!/^[a-z][a-z0-9_]+$/.test(name)) throw new Error('Invalid role');
let target;
try {
  const role = (await source.query('SELECT rolsuper,rolcreatedb,rolcreaterole,rolbypassrls FROM pg_roles WHERE rolname=current_user')).rows[0];
  if (Object.values(role).some(Boolean)) throw new Error('Runtime role has excessive privileges');
  for (const sql of ['CREATE SCHEMA midori_forbidden', 'UPDATE asset_registry SET enabled=false WHERE false']) {
    let denied = false;
    try { await source.query(sql); } catch (error) { denied = error.code === '42501'; }
    if (!denied) throw new Error('Runtime permissions are not restricted');
  }
  const archive = new URL(`../../.local/${destination}.dump`, import.meta.url).pathname;
  await backup(migration.DATABASE_URL, archive);
  await admin.query(`CREATE DATABASE "${destination}" OWNER "${ownerRole}" TEMPLATE template0`);
  await admin.query(`REVOKE ALL ON DATABASE "${destination}" FROM PUBLIC`);
  await admin.query(`GRANT CONNECT ON DATABASE "${destination}" TO "${runtimeRole}"`);
  sourceUrl.pathname = `/${destination}`;
  await restore(sourceUrl.href, archive, runtimeRole);
  const appTarget = new URL(runtime.DATABASE_URL); appTarget.pathname = `/${destination}`;
  target = createDatabase(loadConfig({ ...runtime, DATABASE_URL: appTarget.href }));
  const tables = ['wallets', 'devices', 'chain_accounts', 'asset_registry', 'account_assets', 'auth_challenges', 'authenticators', 'sessions', 'auth_attempts', 'request_nonces', 'encrypted_blobs', 'schema_migrations'];
  const reconciliation = {};
  for (const table of tables) {
    const sql = `SELECT row_to_json(t)::text AS record FROM ${table} t ORDER BY row_to_json(t)::text`;
    const before = (await source.query(sql)).rows.map(row => row.record);
    const after = (await target.query(sql)).rows.map(row => row.record);
    if (sha256(JSON.stringify(before)) !== sha256(JSON.stringify(after))) throw new Error(`Live backup mismatch in ${table}`);
    reconciliation[table] = { rows: before.length, equal: true };
  }
  await target.close(); target = null;
  await source.close();
  const hanging = (await admin.query("SELECT count(*)::int AS count FROM pg_stat_activity WHERE datname=$1 AND application_name='midori-api'", [destination])).rows[0].count;
  if (hanging) throw new Error('Restore verification left pooled connections open');
  const result = { checkedAt: new Date().toISOString(), source: new URL(runtime.DATABASE_URL).pathname.slice(1), restored: destination, runtimeRole, runtimePrivileges: role, ownerCredentialsExcludedFromRuntime: true, backup: 'custom archive in ignored .local directory', reconciliation, remainingRestoreConnections: hanging };
  await writeFile(new URL('../../docs/evidencia/ejecucion/f2-live-restore.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ event: 'live_account_restored', tables: tables.length, walletCount: reconciliation.wallets.rows, runtimePrivilegesRestricted: true, remainingConnections: hanging }));
} finally { if (target) await target.close(); await source.close(); await admin.end(); }
