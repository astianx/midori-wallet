import { createDatabase } from '../lib/db.js';
import { loadConfig } from '../lib/config.js';
import { migrate } from '../lib/migrations.js';

const config = loadConfig({ ...process.env, DATABASE_URL: process.env.MIDORI_MIGRATION_DATABASE_URL || process.env.DATABASE_URL });
const db = createDatabase(config);
try { console.log(JSON.stringify({ applied: await migrate(db) })); }
catch { console.error('Migration failed; check migration history, ownership and database availability'); process.exitCode = 1; }
finally { await db.close(); }
