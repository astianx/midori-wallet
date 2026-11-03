import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { parseEnv } from 'node:util';
const root = fileURLToPath(new URL('../',import.meta.url));
const target = 'electron';
const pkg = JSON.parse(fs.readFileSync(path.join(root,'package.json')));
if (!['web','electron','android','ios'].includes(target)) throw new Error('Choose web, electron, android or ios');
if (target === 'ios' && process.platform !== 'darwin') throw new Error('An iOS build requires macOS and Xcode');
const native = ['android','ios'].includes(target);
const platform = target === 'electron' ? process.platform : target;
const type = native ? 'phonegap' : target;
const profile = process.env.MIDORI_BUILD_PROFILE || 'debug';
if (!['debug','release'].includes(profile)) throw new Error('Invalid build profile');
const dir = target === 'web' ? 'web' : native ? 'phonegap' : 'electron';
const envPath = path.join(root,dir,'.env');
const local = fs.existsSync(envPath) ? parseEnv(fs.readFileSync(envPath,'utf8')) : {};
const api = process.env.VITE_API_ORIGIN || (target === 'web' ? '' : local.VITE_API_ORIGIN || 'https://wallet.astian.org');
if (api) {
  const url = new URL(api);
  if (url.username || url.password || url.origin !== api.replace(/\/$/,'') || (url.protocol !== 'https:' && !(profile === 'debug' && ['localhost','127.0.0.1','10.0.2.2'].includes(url.hostname) && url.protocol === 'http:'))) throw new Error('Build API requires HTTPS; local HTTP is available in debug only');
}
const environment = { ...process.env, VITE_BUILD_TYPE: type, VITE_PLATFORM: platform, VITE_DISTRIBUTION: target === 'electron' ? platform : native ? target === 'android' ? 'android-apk' : 'ios' : 'web', VITE_VERSION: pkg.version, VITE_NAME: 'midori-wallet', VITE_API_ORIGIN: api, VITE_SITE_URL: 'https://wallet.astian.org/', VITE_SITE_URL_TOR: '', VITE_API_CSP: api || '', VITE_API_CSP_TOR: '', MIDORI_SIGN_ARTIFACTS: 'false', MIDORI_ALLOW_PUBLISH: 'false' };
const run = (file,args,cwd = root) => execFileSync(process.execPath,[file,...args],{cwd,env:environment,stdio:'inherit'});
run(path.join(root,'web/node_modules/vite/bin/vite.js'),['build'],path.join(root,'web'));
if (target === 'electron') {
  fs.copyFileSync(path.join(root,'packages/update-core/index.js'),path.join(root,'electron/dist/update-core.js'));
  run(path.join(root,'electron/node_modules/@electron-forge/cli/dist/electron-forge.js'),['make',`--platform=${platform}`,`--arch=${process.arch}`],path.join(root,'electron'));
}
if (platform === 'darwin') {
  const appPath = path.join(root, 'electron/out', `Midori Wallet-darwin-${process.arch}`, 'Midori Wallet.app');
  execFileSync('hdiutil', ['create','-volname','Midori Wallet','-srcfolder',appPath,'-ov','-format','UDZO',path.join(root,'electron/out/make',`Midori-Wallet-${pkg.version}-${process.arch}.dmg`)], { stdio:'inherit' });
}
if (native) run(path.join(root,'phonegap/native-build.mjs'),[target],path.join(root,'phonegap'));
fs.mkdirSync(path.join(root,'artifacts'),{recursive:true});
fs.writeFileSync(path.join(root,'artifacts',`${target}-${platform}-build.json`),JSON.stringify({ product:'Midori Wallet', version:pkg.version, target, platform, arch:process.arch, profile, node:process.version, commit:process.env.GITHUB_SHA || 'local-uncommitted', signed:false, published:false },null,2)+'\n');
