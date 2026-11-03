import { readFile, readdir } from 'node:fs/promises';
import { sha256 } from './crypto.js';

export async function migrationFiles() {
  const directory = new URL('../migrations/', import.meta.url);
  const names = (await readdir(directory)).filter(name => /^\d{3}_[a-z0-9_]+\.sql$/.test(name)).sort();
  return Promise.all(names.map(async version => {
    const sql = await readFile(new URL(version, directory), 'utf8');
    return { version, sql, checksum: sha256(sql) };
  }));
}

export async function migrate(db) {
  const files = await migrationFiles();
  return db.transaction(async client => {
    await client.query('SELECT pg_advisory_xact_lock(738194260)');
    await client.query('CREATE SCHEMA IF NOT EXISTS midori');
    await client.query('REVOKE ALL ON SCHEMA midori FROM PUBLIC');
    await client.query('CREATE TABLE IF NOT EXISTS midori.schema_migrations (version text PRIMARY KEY, checksum text NOT NULL, applied_at timestamptz NOT NULL DEFAULT clock_timestamp())');
    const { rows } = await client.query('SELECT version, checksum FROM midori.schema_migrations ORDER BY version');
    if (rows.some(row => !files.some(file => file.version === row.version && file.checksum === row.checksum))) throw new Error('Applied migration differs from source');
    const applied = [];
    for (const file of files) {
      if (rows.some(row => row.version === file.version)) continue;
      await client.query(file.sql);
      await client.query('INSERT INTO midori.schema_migrations(version, checksum) VALUES ($1, $2)', [file.version, file.checksum]);
      applied.push(file.version);
    }
    return applied;
  });
}

export async function checkSchema(db) {
  const files = await migrationFiles();
  const rows = await db.ready();
  return rows.length === files.length && files.every(file => rows.some(row => file.version === row.version && file.checksum === row.checksum));
}
