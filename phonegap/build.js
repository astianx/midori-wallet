import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { loadBuildEnvironment, resolveBuildUrls } from '../config/environment.js';

const directory = fileURLToPath(new URL('.', import.meta.url));
const platform = process.env.VITE_PLATFORM || process.argv[2];
if (!['android','ios'].includes(platform)) throw new Error('Choose android or ios');
if (platform === 'ios' && process.platform !== 'darwin') throw new Error('iOS requires macOS and Xcode');
const env = loadBuildEnvironment(directory);
const profile = env.MIDORI_BUILD_PROFILE || 'debug';
if (!['debug','release'].includes(profile)) throw new Error('Invalid build profile');
const release = profile === 'release';
const { siteUrl, apiOrigin } = resolveBuildUrls(env, { native: true, release });
const api = new URL(apiOrigin);
const root = path.resolve(directory, '..');
const versionInfo = JSON.parse(fs.readFileSync(path.join(directory, 'package.json')));
execFileSync(process.execPath, [path.join(root, 'web/node_modules/vite/bin/vite.js'), 'build'], { cwd: path.join(root,'web'), stdio:'inherit', env:{ ...env, VITE_BUILD_TYPE:'phonegap', VITE_PLATFORM:platform, VITE_DISTRIBUTION:platform === 'android' ? 'android-apk' : 'ios', VITE_VERSION:versionInfo.version, VITE_NAME:'midori-wallet', VITE_BUILD_PROFILE:profile, VITE_SITE_URL:siteUrl, VITE_API_ORIGIN:api.origin, VITE_API_CSP:api.origin, VITE_API_CSP_TOR:'', VITE_SITE_URL_TOR:'', MIDORI_SIGN_ARTIFACTS:'false', MIDORI_ALLOW_PUBLISH:'false' } });
const build = path.join(directory, 'build-native', release ? 'release' : 'debug');
fs.mkdirSync(build, { recursive: true });
fs.cpSync(path.join(directory,'build/www'), path.join(build,'www'), { recursive: true });
const version = JSON.parse(fs.readFileSync(path.join(directory,'package.json'))).version;
const template = fs.readFileSync(path.join(directory,'config.xml.template'),'utf8');
const xml = value => value.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;');
const config = template.replaceAll('{{id}}', release ? 'org.astian.miwallet' : 'org.astian.miwallet.beta').replaceAll('{{name}}', release ? 'Midori Wallet' : 'Midori Wallet Beta').replaceAll('{{version}}', version).replaceAll('{{api}}',xml(api.origin)).replaceAll('{{cleartext}}', String(api.protocol === 'http:'));
fs.writeFileSync(path.join(build,'config.xml'), config);
if (!fs.existsSync(path.join(build,'package.json'))) fs.writeFileSync(path.join(build,'package.json'),JSON.stringify({ name: release ? 'midori-wallet' : 'midori-wallet-beta', version, private: true }));
// Release signing is opt-in: when the four ANDROID_KEYSTORE_* variables are
// present (CI secrets, or a local .env for a one-off signed build), write
// Cordova's build.json so `cordova build android --release` produces a
// signed, zipaligned app-release.apk instead of app-release-unsigned.apk.
// Without them, release builds remain unsigned, matching prior behavior.
if (platform === 'android' && release) {
  const { ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD } = env;
  if (ANDROID_KEYSTORE_PATH && ANDROID_KEYSTORE_PASSWORD && ANDROID_KEY_ALIAS && ANDROID_KEY_PASSWORD) {
    fs.writeFileSync(path.join(build,'build.json'), JSON.stringify({ android: { release: {
      keystore: path.resolve(ANDROID_KEYSTORE_PATH), storePassword: ANDROID_KEYSTORE_PASSWORD,
      alias: ANDROID_KEY_ALIAS, password: ANDROID_KEY_PASSWORD, keystoreType: 'pkcs12',
    } } }));
  }
}
const cli = path.join(directory,'node_modules/cordova/bin/cordova');
const run = args => execFileSync(process.execPath,[cli,...args],{cwd:build,env:{...env,CI:'true',CORDOVA_TELEMETRY_OPTOUT:'1',npm_config_ignore_scripts:'true',npm_config_audit:'false',npm_config_fund:'false'},stdio:'inherit'});
const platformPackage = platform === 'android' ? 'cordova-android' : 'cordova-ios';
if (!fs.existsSync(path.join(build,'platforms',platform))) run(['platform','add',`${platformPackage}@${versionInfo.dependencies[platformPackage]}`,'--save']);
const scheme = release ? 'midoriwallet' : 'midoriwallet-beta';
const plugins = [ ['org.astian.midori.vault',path.join(directory,'plugins/midori-vault')], ['cordova-plugin-inappbrowser','cordova-plugin-inappbrowser@7.0.0'], ['cordova-plugin-customurlscheme','cordova-plugin-customurlscheme@5.0.2'], ['cordova-plugin-fingerprint-aio','cordova-plugin-fingerprint-aio@6.0.1'] ];
for (const [id,source] of plugins) if (!fs.existsSync(path.join(build,'plugins',id))) run(['plugin','add',source,'--save', ...(id === 'cordova-plugin-customurlscheme' ? ['--variable',`URL_SCHEME=${scheme}`] : [])]);
run(['prepare',platform]);
if (platform === 'android') {
  // Google Play requires an .aab (Android App Bundle) for new app
  // submissions; it no longer accepts a plain .apk. Release builds produce
  // both from the same prepared project: the .apk for direct/sideload
  // distribution (GitHub release, manual install) and the .aab for Play
  // Store. Debug builds keep producing only the .apk, which is what's
  // actually installed/tested locally or in CI.
  for (const packageType of release ? ['apk','bundle'] : ['apk']) run(['build','android',release ? '--release' : '--debug','--',`--packageType=${packageType}`]);
} else {
  run(['build','ios','--debug','--emulator','--buildFlag=CODE_SIGNING_ALLOWED=NO']);
}
