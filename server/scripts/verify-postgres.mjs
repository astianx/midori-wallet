import pg from 'pg';
import { randomBytes } from 'node:crypto';
import { spawn } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { backup } from './backup.mjs';
import { restore } from './restore.mjs';
import { sha256 } from '../lib/crypto.js';

if (!process.env.MIDORI_PG_ADMIN_URL) throw new Error('MIDORI_PG_ADMIN_URL required to create isolated verification databases');
const adminUrl = process.env.MIDORI_PG_ADMIN_URL;
const admin = new pg.Client({ connectionString: adminUrl });
await admin.connect();
const suffix = randomBytes(6).toString('hex');
const sourceName = `midori_verify_${suffix}`; const restoredName = `midori_restore_${suffix}`;
const runtimeRole = `midori_check_${suffix}`;
const runtimePassword = randomBytes(32).toString('base64url');
const urlFor = name => { const url = new URL(adminUrl); url.pathname = `/${name}`; return url.href; };
await admin.query(`CREATE DATABASE "${sourceName}" TEMPLATE template0`);
await admin.query(`CREATE DATABASE "${restoredName}" TEMPLATE template0`);
const statement = await admin.query("SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD %L', $1::text, $2::text) AS sql", [runtimeRole, runtimePassword]);
await admin.query(statement.rows[0].sql);
await admin.end();
const sourceUrl = urlFor(sourceName); const restoredUrl = urlFor(restoredName);
const runtimeUrl = new URL(sourceUrl); runtimeUrl.username = runtimeRole; runtimeUrl.password = runtimePassword;
const startedAt = new Date().toISOString();
const code = await new Promise((resolve, reject) => {
  const child = spawn(process.execPath, ['--test', 'test/unit.test.mjs', 'test/postgres.test.mjs', 'test/compat.test.mjs'], { cwd: new URL('../', import.meta.url), env: { ...process.env, MIDORI_TEST_DATABASE_URL: sourceUrl, MIDORI_TEST_RUNTIME_DATABASE_URL: runtimeUrl.href }, stdio: 'inherit' });
  child.once('error', reject); child.once('exit', resolve);
});
if (code !== 0) { console.error('Verification failed; isolated databases retained for inspection'); process.exitCode = 1; }
else {
  const file = new URL(`../../.local/f2-verify-${suffix}.dump`, import.meta.url).pathname;
  await backup(sourceUrl, file);
  const restored = await restore(restoredUrl, file);
  const source = createDatabase(loadConfig({ DATABASE_URL: sourceUrl }));
  const target = createDatabase(loadConfig({ DATABASE_URL: restoredUrl }));
  const tables = ['schema_migrations', 'wallets', 'devices', 'chain_accounts', 'asset_registry', 'account_assets', 'auth_challenges', 'authenticators', 'sessions', 'auth_attempts', 'request_nonces', 'encrypted_blobs'];
  const reconciliation = {};
  try {
    for (const table of tables) {
      const query = `SELECT row_to_json(t)::text AS record FROM ${table} t ORDER BY row_to_json(t)::text`;
      const left = (await source.query(query)).rows.map(row => row.record); const right = (await target.query(query)).rows.map(row => row.record);
      const digest = sha256(JSON.stringify(left));
      if (digest !== sha256(JSON.stringify(right))) throw new Error(`Restore mismatch for ${table}`);
      reconciliation[table] = { rows: left.length, sha256: digest, equal: true };
    }
    let refusedNonempty = false;
    try { await restore(restoredUrl, file); } catch { refusedNonempty = true; }
    if (!refusedNonempty) throw new Error('Restore must refuse a nonempty destination');
    const result = { startedAt, finishedAt: new Date().toISOString(), source: sourceName, restored: restoredName, checks: 'real PostgreSQL and HTTP; actual WebAuthn signature verification with generated authenticator keys', backup: { restored, refusedNonempty }, reconciliation, credentialsLogged: false };
    await writeFile(new URL('../../docs/evidencia/ejecucion/f2-verification.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
    console.log(JSON.stringify({ event: 'restore_reconciled', tables: tables.length, source: sourceName, restored: restoredName, equal: true }));
  } finally { await source.close(); await target.close(); }
}
