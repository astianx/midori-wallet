import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseEnv } from 'node:util';

const root = fileURLToPath(new URL('../', import.meta.url));

export function loadBuildEnvironment(directory, environment = process.env) {
  const read = folder => {
    const file = path.join(folder, '.env');
    return fs.existsSync(file) ? parseEnv(fs.readFileSync(file, 'utf8')) : {};
  };
  return { ...read(root), ...read(directory), ...environment };
}

export function resolveBuildUrls(env, { native = false, release = false } = {}) {
  let site;
  try { site = new URL(env.VITE_SITE_URL); } catch { throw new Error('Configure VITE_SITE_URL in the root or platform .env'); }
  const localHosts = ['localhost', '127.0.0.1', '10.0.2.2'];
  if (site.username || site.password || site.search || site.hash || (site.protocol !== 'https:' && !(site.protocol === 'http:' && localHosts.includes(site.hostname)))) throw new Error('VITE_SITE_URL must be an HTTPS URL or local HTTP URL without credentials, query or fragment');
  if (!site.pathname.endsWith('/')) site.pathname += '/';
  const apiOrigin = env.VITE_API_ORIGIN || (native ? site.origin : '');
  if (apiOrigin) {
    let api;
    try { api = new URL(apiOrigin); } catch { throw new Error('Invalid VITE_API_ORIGIN'); }
    if (api.username || api.password || api.origin !== api.href.replace(/\/$/, '') || (api.protocol !== 'https:' && !(api.protocol === 'http:' && !release && localHosts.includes(api.hostname)))) throw new Error('VITE_API_ORIGIN must be an HTTPS origin without a path; debug HTTP is limited to local hosts');
  }
  return { siteUrl: site.href, apiOrigin: apiOrigin ? new URL(apiOrigin).origin : '' };
}
