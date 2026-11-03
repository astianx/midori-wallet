import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { createApp } from './app.js';
import { createDatabase } from './lib/db.js';
import { loadConfig } from './lib/config.js';

export async function startServer(config = loadConfig()) {
  const logger = entry => console.error(JSON.stringify(entry));
  const db = createDatabase(config, logger);
  const { createCompatibilityApp } = await import('./compat/app.js');
  const app = createApp({ db, config, logger, compatibilityApp: createCompatibilityApp({ allowedOrigins: config.allowedOrigins, persistence: 'PostgreSQL; API v1' }) });
  const server = createServer(app);
  server.requestTimeout = 30000;
  server.headersTimeout = 10000;
  server.keepAliveTimeout = 5000;
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(config.port, config.host, resolve); });
  let closing;
  const close = () => closing ||= (async () => {
    const deadline = setTimeout(() => { server.closeAllConnections(); process.exitCode = 1; }, 10000).unref();
    await new Promise(resolve => server.close(resolve));
    await db.close();
    clearTimeout(deadline);
  })();
  console.log(JSON.stringify({ event: 'api_listening', host: config.host, port: config.port, apiVersion: 'v1', network: 'base', chainId: 8453 }));
  return { server, db, close };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const running = await startServer();
    for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => running.close().catch(() => { process.exitCode = 1; }));
  } catch { console.error(JSON.stringify({ event: 'startup_failed', message: 'Check API and PostgreSQL configuration' })); process.exitCode = 1; }
}
