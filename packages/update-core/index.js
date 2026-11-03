import { createPublicKey, verify } from 'node:crypto';

export const repository = 'astianx/midori-wallet';
const versionPattern = /^v?(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})(?:-beta\.(0|[1-9][0-9]{0,8}))?$/;
export function compareVersions(left, right) {
  const a = versionPattern.exec(left), b = versionPattern.exec(right);
  if (!a || !b) throw new Error('Invalid release version');
  for (let i = 1; i <= 3; i++) if (a[i] !== b[i]) return Number(a[i]) - Number(b[i]);
  return (a[4] === undefined ? Infinity : Number(a[4])) === (b[4] === undefined ? Infinity : Number(b[4])) ? 0 : a[4] === undefined ? 1 : b[4] === undefined ? -1 : Number(a[4]) - Number(b[4]);
}

export function updateConfig(env = {}) {
  const repo = env.MIDORI_UPDATE_REPOSITORY;
  const publicKey = env.MIDORI_UPDATE_PUBLIC_KEY;
  const channel = env.MIDORI_UPDATE_CHANNEL;
  if (!repo && !publicKey && !channel) return null;
  if (repo !== repository || !['stable','beta'].includes(channel) || !/^[A-Za-z0-9_-]{43}$/.test(publicKey || '')) throw new Error('Configure an owned update repository, channel and Ed25519 public key');
  return Object.freeze({ repository: repo, channel, publicKey });
}

export function releaseUrl(value, tag) {
  const url = new URL(value);
  if (url.origin !== 'https://github.com' || url.username || url.password || url.search || url.hash || !url.pathname.startsWith(`/${repository}/releases/download/${tag}/`) || url.pathname.split('/').length !== 7 || !/^[a-zA-Z0-9._-]+$/.test(url.pathname.split('/').at(-1))) throw new Error('Untrusted update asset');
  return url.href;
}

export function verifyManifest(bytes, signature, config, tag) {
  if (!Buffer.isBuffer(bytes) || bytes.length > 65536 || !versionPattern.test(tag) || !/^[A-Za-z0-9_-]{86}$/.test(signature)) throw new Error('Invalid update manifest');
  const key = createPublicKey({ key: Buffer.concat([Buffer.from('302a300506032b6570032100','hex'), Buffer.from(config.publicKey,'base64url')]), type: 'spki', format: 'der' });
  if (!verify(null, bytes, key, Buffer.from(signature, 'base64url'))) throw new Error('Invalid update signature');
  const manifest = JSON.parse(bytes.toString('utf8'));
  if (manifest.schema !== 'midori.updates' || manifest.version !== 1 || manifest.channel !== config.channel || manifest.release !== tag || !Array.isArray(manifest.assets) || manifest.assets.length > 30) throw new Error('Invalid update manifest');
  if ((config.channel === 'stable') === tag.includes('-beta.')) throw new Error('Wrong update channel');
  const seen = new Set();
  return manifest.assets.map(asset => {
    if (!['win32','darwin','linux','android','ios','web'].includes(asset.platform) || !['x64','arm64','any'].includes(asset.arch) || !/^[a-f0-9]{64}$/.test(asset.sha256) || !Number.isSafeInteger(asset.size) || asset.size <= 0 || asset.size > 2 ** 31) throw new Error('Invalid update asset');
    const key = `${asset.platform}:${asset.arch}`;
    if (seen.has(key)) throw new Error('Duplicate update platform');
    seen.add(key);
    return Object.freeze({ platform: asset.platform, arch: asset.arch, version: tag.replace(/^v/,''), channel: config.channel, sha256: asset.sha256, size: asset.size, url: releaseUrl(asset.url, tag) });
  });
}

async function boundedBody(response, max) {
  if (!response.ok) throw new Error('Update service unavailable');
  const chunks = [];
  let size = 0;
  for await (const chunk of response.body) {
    size += chunk.length;
    if (size > max) throw new Error('Oversized update response');
    chunks.push(Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}

export function createGitHubUpdates({ env = {}, fetchImpl = fetch, now = Date.now } = {}) {
  const config = updateConfig(env);
  let cache = [], refreshed = -Infinity, pending, failedAt = -Infinity;
  const request = async (url, max = 1024 * 1024) => boundedBody(await fetchImpl(url, { redirect: 'error', headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'Midori-Wallet-Updater', 'X-GitHub-Api-Version': '2022-11-28' }, signal: AbortSignal.timeout(15000) }), max);
  const load = async () => {
    if (!config) return [];
    if (now() - refreshed < 300000) return cache;
    const releases = JSON.parse((await request(`https://api.github.com/repos/${repository}/releases?per_page=30`)).toString('utf8'));
    if (!Array.isArray(releases)) throw new Error('Invalid release response');
    const release = releases.filter(item => !item.draft && versionPattern.test(item.tag_name) && (config.channel === 'stable' ? !item.prerelease && !item.tag_name.includes('-beta.') : item.prerelease && item.tag_name.includes('-beta.'))).sort((a,b) => compareVersions(b.tag_name,a.tag_name))[0];
    if (!release) { cache = []; refreshed = now(); return cache; }
    const name = `midori-updates-${config.channel}.json`;
    const manifestAsset = release.assets?.find(asset => asset.name === name);
    const signatureAsset = release.assets?.find(asset => asset.name === `${name}.sig`);
    if (!manifestAsset || !signatureAsset) throw new Error('Release has no signed Midori manifest');
    const download = async asset => {
      const canonical = releaseUrl(asset.browser_download_url, release.tag_name);
      // GitHub's asset endpoint redirects downloads to a CDN. Follow only its fixed HTTPS CDN hosts.
      let response = await fetchImpl(canonical, { redirect: 'manual', headers: { 'User-Agent': 'Midori-Wallet-Updater' }, signal: AbortSignal.timeout(15000) });
      if ([301,302,303,307,308].includes(response.status)) {
        const location = new URL(response.headers.get('location'));
        if (location.protocol !== 'https:' || !['release-assets.githubusercontent.com','objects.githubusercontent.com'].includes(location.hostname) || location.username || location.password || location.port) throw new Error('Untrusted asset redirect');
        response = await fetchImpl(location.href, { redirect: 'error', signal: AbortSignal.timeout(15000) });
      }
      return boundedBody(response, asset === signatureAsset ? 256 : 65536);
    };
    const [bytes, signature] = await Promise.all([download(manifestAsset), download(signatureAsset)]);
    const verified = verifyManifest(bytes, signature.toString('utf8').trim(), config, release.tag_name);
    cache = verified; refreshed = now(); return cache;
  };
  return {
    account: config?.repository,
    enabled: !!config,
    async sync() { return this.getUpdates(); },
    async getUpdates() {
      if (now() - failedAt < 60000) throw new Error('Update service is temporarily unavailable');
      pending ||= load().catch(error => { failedAt = now(); throw error; }).finally(() => { pending = null; });
      return pending;
    },
    async getUpdate(platform, arch) { return (await this.getUpdates()).find(item => item.platform === platform && [arch,'any'].includes(item.arch)); },
  };
}
