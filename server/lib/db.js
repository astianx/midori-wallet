import pg from 'pg';

export function createDatabase(config, onError = () => {}) {
  const pool = new pg.Pool({
    connectionString: config.connectionString,
    max: config.poolSize,
    connectionTimeoutMillis: 3000,
    idleTimeoutMillis: 10000,
    statement_timeout: 10000,
    idle_in_transaction_session_timeout: 10000,
    application_name: 'midori-api',
    options: '-c search_path=midori,pg_catalog',
  });
  pool.on('error', error => onError({ event: 'database_error', code: error.code || 'UNAVAILABLE' }));
  let closing;
  return {
    query: (sql, values) => pool.query(sql, values),
    async transaction(action) {
      const client = await pool.connect();
      let broken = false;
      try {
        await client.query('BEGIN');
        const value = await action(client);
        await client.query('COMMIT');
        return value;
      } catch (error) {
        try { await client.query('ROLLBACK'); } catch { broken = true; }
        throw error;
      } finally { client.release(broken); }
    },
    async ready() {
      const result = await pool.query("SELECT version, checksum FROM schema_migrations ORDER BY version");
      return result.rows;
    },
    close: () => closing ||= pool.end(),
  };
}
