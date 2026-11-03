import { spawn } from 'node:child_process';
import { chmod, open, unlink } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export function pgEnvironment(connectionString) {
  const url = new URL(connectionString);
  const env = { ...process.env, PGHOST: url.hostname, PGPORT: url.port || '5432', PGUSER: decodeURIComponent(url.username), PGPASSWORD: decodeURIComponent(url.password), PGDATABASE: url.pathname.slice(1), PGCONNECT_TIMEOUT: '5' };
  if (url.searchParams.has('sslmode')) env.PGSSLMODE = url.searchParams.get('sslmode');
  for (const key of ['DATABASE_URL', 'MIDORI_MIGRATION_DATABASE_URL', 'MIDORI_PG_ADMIN_URL']) delete env[key];
  return env;
}

export async function runPg(command, args, connectionString) {
  await new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { env: pgEnvironment(connectionString), stdio: ['ignore', 'ignore', 'pipe'] });
    child.stderr.resume();
    child.once('error', () => reject(new Error(`${command} could not start`)));
    child.once('exit', code => code === 0 ? resolvePromise() : reject(new Error(`${command} failed; check database access and archive compatibility`)));
  });
}

export async function backup(connectionString, filename) {
  const file = resolve(filename);
  const handle = await open(file, 'wx', 0o600); await handle.close();
  try {
    await runPg('pg_dump', ['--format=custom', '--schema=midori', '--no-owner', '--no-acl', '--file', file], connectionString);
    await chmod(file, 0o600);
    return { file, format: 'pg_dump custom', scope: 'midori' };
  } catch (error) { await unlink(file); throw error; }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (!process.argv[2]) throw new Error('Usage: npm run db:backup -- /absolute/path/backup.dump');
    console.log(JSON.stringify(await backup(process.env.MIDORI_MIGRATION_DATABASE_URL || process.env.DATABASE_URL, process.argv[2])));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
