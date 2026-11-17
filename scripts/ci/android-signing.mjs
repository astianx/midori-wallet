import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { androidSigningConfig } from '../../config/android-signing.js';
import { sha256File } from './artifacts.mjs';

const root = fileURLToPath(new URL('../../', import.meta.url));
const tool = name => path.join(process.env.JAVA_HOME, 'bin', name);
const run = (command, args) => {
  try { return execFileSync(command, args, { encoding: 'utf8', timeout: 120000, stdio: ['ignore', 'pipe', 'pipe'] }); }
  catch { throw new Error(`${path.basename(command)} signature verification failed`); }
};

export function decodeKeystore(value) {
  const encoded = (value || '').replace(/\s/g, '');
  const bytes = Buffer.from(encoded, 'base64');
  if (!encoded || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded) || bytes.toString('base64').replace(/=+$/, '') !== encoded.replace(/=+$/, '')) throw new Error('ANDROID_KEYSTORE_BASE64 is missing or invalid');
  return bytes;
}

export function prepareSigning(env = process.env) {
  const destination = path.join(env.RUNNER_TEMP, 'midori-wallet-release.p12');
  const signing = { ...env, ANDROID_KEYSTORE_PATH: destination, MIDORI_SIGN_ARTIFACTS: 'true' };
  fs.writeFileSync(destination, decodeKeystore(env.ANDROID_KEYSTORE_BASE64), { mode: 0o600 });
  try {
    androidSigningConfig(signing);
    execFileSync(path.join(env.JAVA_HOME, 'bin/keytool'), ['-list', '-keystore', destination, '-storetype', 'PKCS12', '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD', '-alias', env.ANDROID_KEY_ALIAS], {
      env: signing, timeout: 30000, stdio: ['ignore', 'pipe', 'pipe'],
    });
    fs.appendFileSync(env.GITHUB_ENV, `ANDROID_KEYSTORE_PATH=${destination}\n`);
  } catch {
    fs.rmSync(destination, { force: true });
    throw new Error('Android signing preflight failed: check the PKCS12 keystore, alias and passwords');
  }
}

export async function verifySigning() {
  const signing = androidSigningConfig({ ...process.env, MIDORI_SIGN_ARTIFACTS: 'true' });
  const directory = path.join(root, 'phonegap/build-native/release/platforms/android/app/build/outputs');
  const apk = path.join(directory, 'apk/release/app-release.apk');
  const aab = path.join(directory, 'bundle/release/app-release.aab');
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  const result = run(path.join(sdk, 'build-tools/36.0.0/apksigner'), ['verify', '--verbose', '--print-certs', apk]);
  const certificate = execFileSync(tool('keytool'), ['-exportcert', '-keystore', signing.keystore, '-storetype', 'PKCS12', '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD', '-alias', signing.alias], {
    timeout: 30000, stdio: ['ignore', 'pipe', 'pipe'],
  });
  const certificateSha256 = createHash('sha256').update(certificate).digest('hex');
  const apkCertificates = [...result.matchAll(/Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)/gi)].map(match => match[1].toLowerCase());
  if (apkCertificates.length !== 1 || apkCertificates[0] !== certificateSha256) throw new Error('APK signing certificate differs from the release keystore');
  run(tool('jarsigner'), ['-verify', '-strict', '-keystore', signing.keystore, '-storetype', 'PKCS12', '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD', aab, signing.alias]);
  const verification = { certificateSha256, files: { '.apk': await sha256File(apk), '.aab': await sha256File(aab) } };
  fs.mkdirSync(path.join(root, 'artifacts'), { recursive: true });
  fs.writeFileSync(path.join(root, 'artifacts/android-signatures.json'), JSON.stringify(verification, null, 2) + '\n');
  console.log('APK and AAB signatures match the release keystore');
}

export function cleanupSigning(env = process.env) {
  fs.rmSync(path.join(env.RUNNER_TEMP, 'midori-wallet-release.p12'), { force: true });
  fs.rmSync(path.join(root, 'phonegap/build-native/release/build.json'), { force: true });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const actions = { prepare: prepareSigning, verify: verifySigning, cleanup: cleanupSigning };
  const action = actions[process.argv[2]];
  if (!action) throw new Error('Choose prepare, verify or cleanup');
  try { await action(); }
  catch (error) { console.error(`Android signing ${process.argv[2]} failed: ${error.message}`); process.exitCode = 1; }
}
