import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const directory = fileURLToPath(new URL('.', import.meta.url));
const root = path.resolve(directory, '..');
const pkg = JSON.parse(fs.readFileSync(path.join(directory, 'package.json')));
const profile = process.env.MIDORI_BUILD_PROFILE || 'debug';
if (!['debug', 'release'].includes(profile)) throw new Error('Invalid build profile');
const api = new URL(process.env.VITE_API_ORIGIN || 'https://wallet.astian.org');
if (api.username || api.password || api.origin !== api.href.replace(/\/$/, '') || (api.protocol !== 'https:' && !(profile === 'debug' && api.protocol === 'http:' && ['localhost','127.0.0.1'].includes(api.hostname)))) throw new Error('Configure an HTTPS API origin; debug HTTP is limited to local hosts');
const env = { ...process.env, VITE_BUILD_TYPE:'electron', VITE_PLATFORM:process.platform, VITE_DISTRIBUTION:process.platform, VITE_VERSION:pkg.version, VITE_NAME:'midori-wallet', VITE_BUILD_PROFILE:profile, VITE_API_ORIGIN:api.origin, VITE_API_CSP:api.origin, VITE_SITE_URL:'https://wallet.astian.org/', VITE_SITE_URL_TOR:'', VITE_API_CSP_TOR:'', MIDORI_SIGN_ARTIFACTS:'false', MIDORI_ALLOW_PUBLISH:'false' };
const run = (script, args, cwd) => execFileSync(process.execPath, [script, ...args], { cwd, env, stdio:'inherit' });
run(path.join(root,'web/node_modules/vite/bin/vite.js'), ['build'], path.join(root,'web'));
fs.copyFileSync(path.join(root,'packages/update-core/index.js'), path.join(directory,'dist/update-core.js'));
run(path.join(directory,'node_modules/@electron-forge/cli/dist/electron-forge.js'), ['make',`--platform=${process.platform}`,`--arch=${process.arch}`], directory);
if (process.platform === 'darwin') {
  const name = profile === 'release' ? 'Midori Wallet' : 'Midori Wallet Beta';
  const folder = path.join(directory,'out',`${name}-darwin-${process.arch}`);
  execFileSync('hdiutil', ['create','-volname',name,'-srcfolder',folder,'-ov','-format','UDZO',path.join(directory,'out/make',`Midori-Wallet-${pkg.version}-${process.arch}.dmg`)], { stdio:'inherit' });
}
