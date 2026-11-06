import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { loadBuildEnvironment, resolveBuildUrls } from '../config/environment.js';

const directory = fileURLToPath(new URL('.', import.meta.url));
const root = path.resolve(directory, '..');
const pkg = JSON.parse(fs.readFileSync(path.join(directory, 'package.json')));
const configured = loadBuildEnvironment(directory);
const profile = configured.MIDORI_BUILD_PROFILE || 'debug';
if (!['debug', 'release'].includes(profile)) throw new Error('Invalid build profile');
const { siteUrl, apiOrigin } = resolveBuildUrls(configured, { native: true, release: profile === 'release' });
const env = { ...configured, VITE_BUILD_TYPE:'electron', VITE_PLATFORM:process.platform, VITE_DISTRIBUTION:process.platform, VITE_VERSION:pkg.version, VITE_NAME:'midori-wallet', VITE_BUILD_PROFILE:profile, VITE_API_ORIGIN:apiOrigin, VITE_API_CSP:apiOrigin, VITE_SITE_URL:siteUrl, VITE_SITE_URL_TOR:'', VITE_API_CSP_TOR:'', MIDORI_SIGN_ARTIFACTS:'false', MIDORI_ALLOW_PUBLISH:'false' };
const run = (script, args, cwd) => execFileSync(process.execPath, [script, ...args], { cwd, env, stdio:'inherit' });
run(path.join(root,'web/node_modules/vite/bin/vite.js'), ['build'], path.join(root,'web'));
fs.copyFileSync(path.join(root,'packages/update-core/index.js'), path.join(directory,'dist/update-core.js'));
if (process.platform === 'win32') run(path.join(directory,'node_modules/electron-winstaller/script/select-7z-arch.js'), [], path.join(directory,'node_modules/electron-winstaller'));
run(path.join(directory,'node_modules/@electron-forge/cli/dist/electron-forge.js'), ['make',`--platform=${process.platform}`,`--arch=${process.arch}`], directory);
if (process.platform === 'darwin') {
  const name = profile === 'release' ? 'Midori Wallet' : 'Midori Wallet Beta';
  const folder = path.join(directory,'out',`${name}-darwin-${process.arch}`);
  execFileSync('hdiutil', ['create','-volname',name,'-srcfolder',folder,'-ov','-format','UDZO',path.join(directory,'out/make',`Midori-Wallet-${pkg.version}-${process.arch}.dmg`)], { stdio:'inherit' });
}
