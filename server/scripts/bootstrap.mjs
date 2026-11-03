import pg from 'pg';
import { randomBytes } from 'node:crypto';
import { readFile, writeFile, chmod } from 'node:fs/promises';
import { parseEnv } from 'node:util';
import { pathToFileURL } from 'node:url';
import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { migrate } from '../lib/migrations.js';

const identifier = value => {
  if (!/^[a-z][a-z0-9_]{0,50}$/.test(value)) throw new Error('Invalid database or role name');
  return `"${value}"`;
};

export async function provision({ adminUrl, database = 'midori_wallet', envPath, verifyDatabase = false }) {
  const owner = `${database}_owner`; const runtime = `${database}_app`;
  identifier(database); identifier(owner); identifier(runtime);
  const admin = new pg.Client({ connectionString: adminUrl, connectionTimeoutMillis: 3000 });
  await admin.connect();
  let previous = {};
  if (envPath) try { previous = parseEnv(await readFile(envPath, 'utf8')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const migrationPath = envPath && new URL('.env.migrations', typeof envPath === 'string' ? pathToFileURL(envPath) : envPath);
  let migrationEnvironment = {};
  if (migrationPath) try { migrationEnvironment = parseEnv(await readFile(migrationPath, 'utf8')); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const existingOwnerUrl = migrationEnvironment.DATABASE_URL || previous.MIDORI_MIGRATION_DATABASE_URL;
  const ownerPassword = existingOwnerUrl ? new URL(existingOwnerUrl).password : randomBytes(32).toString('base64url');
  const appPassword = previous.DATABASE_URL ? new URL(previous.DATABASE_URL).password : randomBytes(32).toString('base64url');
  try {
    const existing = await admin.query('SELECT datname FROM pg_database WHERE datname=$1', [database]);
    if (existing.rowCount && !previous.DATABASE_URL && !verifyDatabase) throw new Error('Database already exists; provide its existing private environment instead of taking ownership');
    for (const [role, password] of [[owner, ownerPassword], [runtime, appPassword]]) {
      const roles = await admin.query('SELECT rolname FROM pg_roles WHERE rolname=$1', [role]);
      if (!roles.rowCount) {
        const statement = await admin.query("SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L', $1::text, $2::text) AS sql", [role, password]);
        await admin.query(statement.rows[0].sql);
      } else if (!previous.DATABASE_URL && !verifyDatabase) throw new Error('Role exists without local configuration; refusing to rotate its credentials');
    }
    if (!existing.rowCount) await admin.query(`CREATE DATABASE ${identifier(database)} OWNER ${identifier(owner)} TEMPLATE template0`);
    await admin.query(`REVOKE ALL ON DATABASE ${identifier(database)} FROM PUBLIC`);
    await admin.query(`GRANT CONNECT ON DATABASE ${identifier(database)} TO ${identifier(runtime)}`);
  } finally { await admin.end(); }
  const makeUrl = (role, password) => {
    const url = new URL(adminUrl); url.username = role; url.password = password; url.pathname = `/${database}`; return url.href;
  };
  const ownerUrl = makeUrl(owner, ownerPassword);
  const values = { ...previous, DATABASE_URL: makeUrl(runtime, appPassword), MIDORI_NETWORK: 'base', MIDORI_CHAIN_ID: '8453', MIDORI_PUBLIC_ORIGIN: previous.MIDORI_PUBLIC_ORIGIN || 'http://localhost:5173', MIDORI_API_HOST: '127.0.0.1', MIDORI_API_PORT: previous.MIDORI_API_PORT || '8080', MIDORI_TRANSACTIONS_ENABLED: 'false', MIDORI_SPONSORSHIP_ENABLED: 'false' };
  for (const key of ['MIDORI_MIGRATION_DATABASE_URL', 'DB_CONNECT', 'DB_NAME', 'CHANGELLY_API_SECRET', 'CHANGENOW_API_KEY', 'API_KEY', 'USERNAME_SALT']) delete values[key];
  const db = createDatabase(loadConfig({ ...values, DATABASE_URL: ownerUrl }));
  try {
    await db.query('REVOKE CREATE ON SCHEMA public FROM PUBLIC');
    const applied = await migrate(db);
    await grantRuntime(db, runtime);
    if (envPath) {
      await writeFile(envPath, Object.entries(values).map(([key, value]) => `${key}=${JSON.stringify(value)}`).join('\n') + '\n', { mode: 0o600 });
      await chmod(envPath, 0o600);
      await writeFile(migrationPath, `DATABASE_URL=${JSON.stringify(ownerUrl)}\nMIDORI_NETWORK=base\nMIDORI_CHAIN_ID=8453\n`, { mode: 0o600 });
      await chmod(migrationPath, 0o600);
    }
    return { database, runtimeRole: runtime, ownerRole: owner, applied, values };
  } finally { await db.close(); }
}

export async function grantRuntime(db, runtime) {
  const name = identifier(runtime);
  await db.query(`GRANT USAGE ON SCHEMA midori TO ${name}`);
  await db.query(`GRANT SELECT ON midori.schema_migrations,midori.asset_registry TO ${name}`);
  await db.query(`GRANT SELECT,INSERT ON midori.wallets,midori.chain_accounts,midori.account_assets TO ${name}`);
  await db.query(`GRANT SELECT,INSERT,UPDATE,DELETE ON midori.chain_events,midori.event_participants,midori.index_cursors,midori.index_checkpoints,midori.wallet_tags,midori.tag_reservations,midori.tag_challenges,midori.tag_resolutions TO ${name}`);
  await db.query(`GRANT USAGE,SELECT ON SEQUENCE midori.chain_events_id_seq TO ${name}`);
  await db.query(`GRANT SELECT,INSERT,UPDATE,DELETE ON midori.tx_intents,midori.user_operations,midori.payment_outbox,midori.sponsor_budgets,midori.sponsor_reservations TO ${name}`);
  await db.query(`GRANT SELECT,INSERT,UPDATE,DELETE ON midori.devices,midori.authenticators,midori.auth_challenges,midori.auth_attempts,midori.sessions,midori.request_nonces,midori.encrypted_blobs TO ${name}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (!process.env.MIDORI_PG_ADMIN_URL) throw new Error('MIDORI_PG_ADMIN_URL is required for initial provisioning');
    const { values, ...summary } = await provision({ adminUrl: process.env.MIDORI_PG_ADMIN_URL, database: process.env.MIDORI_DATABASE_NAME || 'midori_wallet', envPath: new URL('../.env', import.meta.url) });
    console.log(JSON.stringify({ ...summary, configuration: 'server/.env and server/.env.migrations (0600); credentials hidden' }));
  } catch (error) { console.error(JSON.stringify({ event: 'bootstrap_failed', code: error.code || 'CONFIGURATION_ERROR', message: error.code ? 'Check PostgreSQL access and ownership' : error.message })); process.exitCode = 1; }
}
