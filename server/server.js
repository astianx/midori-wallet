import { createServer } from 'node:http';
import express from 'express';
import { fileURLToPath } from 'node:url';
import { pathToFileURL } from 'node:url';
import { createApp } from './app.js';
import { createDatabase } from './lib/db.js';
import { loadConfig } from './lib/config.js';

export async function startServer(config = loadConfig()) {
  const logger = entry => console.error(JSON.stringify(entry));
  const db = createDatabase(config, logger);
  const webApp = express();
  webApp.use((req, res, next) => { res.set('Content-Security-Policy', "frame-ancestors 'none'"); next(); });
  webApp.use(express.static(fileURLToPath(new URL('./dist/', import.meta.url)), { dotfiles:'deny', index:'index.html' }));
  webApp.get('*', (req, res, next) => !req.path.startsWith('/api/') && req.accepts('html') && req.get('accept')?.includes('text/html') ? res.sendFile(fileURLToPath(new URL('./dist/index.html', import.meta.url))) : next());
  const app = createApp({ db, config, logger, webApp });
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
