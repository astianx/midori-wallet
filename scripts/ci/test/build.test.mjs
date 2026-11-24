import { desktopArchitecture } from '../../../config/environment.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { createDmg } from '../../../electron/create-dmg.mjs';
import { androidSigningConfig } from '../../../config/android-signing.js';
import { buildPlan } from '../plan.mjs';
import { collectArtifacts } from '../artifacts.mjs';
import { decodeKeystore, prepareSigning } from '../android-signing.mjs';
import { planReleaseAssets } from '../release.mjs';

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'midori-build-test-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  return {
    root,
    write(name, content) {
      const file = path.join(root, name);
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, content);
      return file;
    },
  };
}

test('signing runs only for main pushes or an explicit manual request on main', () => {
  for (const event of ['push', 'pull_request', 'workflow_dispatch']) {
    for (const ref of ['refs/heads/main', 'refs/heads/new-ui/ux', 'refs/pull/7/merge']) {
      for (const requested of [false, true]) {
        if (event === 'workflow_dispatch' && requested && ref !== 'refs/heads/main') {
          assert.throws(() => buildPlan({ event, ref, requested }), /require main/);
        } else {
          assert.equal(buildPlan({ event, ref, requested }).signedAndroid, ref === 'refs/heads/main' && (event === 'push' || event === 'workflow_dispatch' && requested));
        }
      }
    }
  }
  assert.equal(buildPlan({ event: 'workflow_dispatch', ref: 'refs/heads/main', requested: 'true' }).signedAndroid, true);
});

test('pushing a version tag signs Android and marks a release build regardless of requested/manual inputs', () => {
  for (const ref of ['refs/tags/v1.2.3', 'refs/tags/v1.2.3-beta.1', 'refs/tags/v0.0.1']) {
    const plan = buildPlan({ event: 'push', ref });
    assert.equal(plan.signedAndroid, true);
    assert.equal(plan.releaseTag, true);
  }
});

test('tag refs only trigger a release on push, and only when they look like a release version', () => {
  assert.equal(buildPlan({ event: 'workflow_dispatch', ref: 'refs/tags/v1.2.3' }).releaseTag, false);
  assert.equal(buildPlan({ event: 'pull_request', ref: 'refs/tags/v1.2.3' }).releaseTag, false);
  for (const ref of ['refs/tags/1.2.3', 'refs/tags/v1.2', 'refs/tags/vfoo', 'refs/tags/v1.2.3-rc.1', 'refs/heads/v1.2.3']) {
    const plan = buildPlan({ event: 'push', ref });
    assert.equal(plan.releaseTag, false);
    assert.equal(plan.signedAndroid, false);
  }
});

test('DMG creation retries transient resource contention, uses APFS and verifies the result', async t => {
  const f = fixture(t), destination = path.join(f.root, 'client.dmg'), delays = [];
  let creates = 0, verified = false;
  await createDmg({ name: 'Midori Wallet Beta', folder: f.root, destination }, {
    run(command, args, options) {
      assert.equal(command, 'hdiutil');
      assert.equal(options.timeout, 120000);
      if (args[0] === 'verify') { verified = true; return; }
      assert.equal(args[args.indexOf('-fs') + 1], 'APFS');
      fs.writeFileSync(destination, 'partial');
      if (++creates < 3) throw Object.assign(new Error('busy'), { stderr: 'hdiutil: create failed - Resource busy' });
    },
    sleep: async delay => { assert.equal(fs.existsSync(destination), false); delays.push(delay); },
  });
  assert.equal(creates, 3); assert.equal(verified, true); assert.deepEqual(delays, [2000, 4000]);
});

test('DMG retries stop after three attempts and discard partial output', async t => {
  const f = fixture(t), destination = path.join(f.root, 'client.dmg');
  let calls = 0;
  await assert.rejects(createDmg({ name: 'Midori', folder: f.root, destination }, {
    run() { calls++; fs.writeFileSync(destination, 'partial'); throw Object.assign(new Error('busy'), { stderr: 'Resource busy' }); },
    sleep: async () => {},
  }), /busy/);
  assert.equal(calls, 3); assert.equal(fs.existsSync(destination), false);
});

test('DMG creation does not retry unrelated errors or retain an image that fails verification', async t => {
  const f = fixture(t), destination = path.join(f.root, 'client.dmg');
  for (const failingOperation of ['create', 'verify']) {
    let calls = 0;
    await assert.rejects(createDmg({ name: 'Midori', folder: f.root, destination }, {
      run(command, args) { calls++; fs.writeFileSync(destination, 'unverified'); if (args[0] === failingOperation) throw new Error('Invalid disk image'); },
      sleep: async () => assert.fail('Unrelated errors must not retry'),
    }), /Invalid disk image/);
    assert.equal(calls, failingOperation === 'create' ? 1 : 2);
    assert.equal(fs.existsSync(destination), false);
  }
});

test('signed builds reject missing and partial credentials without exposing their values', t => {
  const f = fixture(t);
  assert.equal(androidSigningConfig({}), null);
  assert.throws(() => androidSigningConfig({ MIDORI_SIGN_ARTIFACTS: 'true' }), /Missing Android signing/);
  const env = { ANDROID_KEYSTORE_PATH: f.write('test.p12', 'temporary'), ANDROID_KEYSTORE_PASSWORD: 'private-password', ANDROID_KEY_ALIAS: 'test' };
  assert.throws(() => androidSigningConfig(env), error => error.message.includes('ANDROID_KEY_PASSWORD') && !error.message.includes('private-password'));
  assert.equal(androidSigningConfig({ ...env, ANDROID_KEY_PASSWORD: 'private-key-password' }).keystoreType, 'pkcs12');
});

test('keystore decoding rejects malformed base64 and accepts multiline encoded data', () => {
  for (const value of ['', 'not base64!', 'A', '====', 'YWJj=garbage']) assert.throws(() => decodeKeystore(value), /missing or invalid/);
  assert.equal(decodeKeystore('dGVt\ncG9yYXJ5\n').toString(), 'temporary');
});

test('signing preflight cleans the decoded keystore when credentials are incomplete', t => {
  const f = fixture(t);
  assert.throws(() => prepareSigning({ RUNNER_TEMP: f.root, ANDROID_KEYSTORE_BASE64: Buffer.from('temporary').toString('base64') }), /preflight failed/);
  assert.equal(fs.existsSync(path.join(f.root, 'midori-wallet-release.p12')), false);
});

test('artifact manifests bind the version and commit to exact file hashes', async t => {
  const f = fixture(t);
  f.write('web/package.json', JSON.stringify({ version: '1.2.3' }));
  f.write('server/dist/index.html', '<html>Midori</html>');
  const manifest = await collectArtifacts('web', { root: f.root, env: { GITHUB_SHA: 'commit-for-validation' } });
  assert.equal(manifest.complete, true); assert.equal(manifest.version, '1.2.3'); assert.equal(manifest.commit, 'commit-for-validation');
  assert.equal(manifest.files[0].sha256, createHash('sha256').update('<html>Midori</html>').digest('hex'));
});

test('a failed macOS DMG preserves its ZIP but marks the artifact set incomplete', async t => {
  const f = fixture(t);
  f.write('electron/package.json', JSON.stringify({ version: '1.2.3' }));
  f.write('electron/out/make/zip/darwin/arm64/client.zip', 'completed zip');
  const manifest = await collectArtifacts('desktop', { root: f.root, platform: 'darwin', env: {} });
  assert.equal(manifest.complete, false); assert.deepEqual(manifest.missing, ['.dmg']);
  assert.equal(fs.readFileSync(path.join(f.root, 'artifacts/desktop/zip/darwin/arm64/client.zip'), 'utf8'), 'completed zip');
});

test('release artifacts cannot be collected after the verified APK is changed', async t => {
  const f = fixture(t);
  f.write('phonegap/package.json', JSON.stringify({ version: '1.2.3' }));
  const output = 'phonegap/build-native/release/platforms/android/app/build/outputs';
  f.write(output + '/apk/release/app-release.apk', 'signed apk');
  f.write(output + '/bundle/release/app-release.aab', 'signed bundle');
  f.write('artifacts/android-signatures.json', JSON.stringify({ certificateSha256: 'certificate', files: {
    '.apk': createHash('sha256').update('signed apk').digest('hex'), '.aab': createHash('sha256').update('signed bundle').digest('hex'),
  } }));
  const manifest = await collectArtifacts('android-release', { root: f.root, env: {} });
  assert.equal(manifest.complete, true); assert.equal(manifest.signing.apkVerified, true); assert.equal(manifest.signing.aabVerified, true);
  f.write(output + '/apk/release/app-release.apk', 'changed apk');
  await assert.rejects(collectArtifacts('android-release', { root: f.root, env: {} }), /differ from the verified/);
});

test('desktop target architecture preserves Apple Silicon artifacts on Intel macOS runners', () => {
  assert.equal(desktopArchitecture('darwin', 'x64', 'arm64'), 'arm64');
  assert.equal(desktopArchitecture('darwin', 'arm64'), 'arm64');
  assert.equal(desktopArchitecture('linux', 'x64'), 'x64');
  assert.equal(desktopArchitecture('win32', 'x64'), 'x64');
  assert.throws(() => desktopArchitecture('darwin', 'x64', 'invalid'), /Unsupported desktop architecture/);
  assert.throws(() => desktopArchitecture('linux', 'x64', 'arm64'), /requires macOS/);
});

test('release asset planning picks one installer per platform/arch and skips CI provenance files', () => {
  const plan = planReleaseAssets([
    'android-release/midori-wallet-android-v1.0.6.apk',
    'android-release/midori-wallet-android-v1.0.6.aab',
    'android-release/manifest.json',
    'desktop-win/squirrel.windows/x64/midori_wallet-1.0.4-full.nupkg',
    'desktop-win/squirrel.windows/x64/RELEASES',
    'desktop-win/squirrel.windows/x64/Midori Wallet Setup.exe',
    'desktop-win/zip/win32/x64/Midori Wallet-win32-x64-1.0.4.zip',
    'desktop-win/manifest.json',
    'desktop-mac/zip/darwin/arm64/Midori Wallet-darwin-arm64-1.0.4.zip',
    'desktop-mac/Midori-Wallet-1.0.4-arm64.dmg',
    'desktop-mac/manifest.json',
    'desktop-linux/zip/linux/x64/Midori Wallet-linux-x64-1.0.4.zip',
    'desktop-linux/deb/x64/midori-wallet_1.0.4_amd64.deb',
    'desktop-linux/manifest.json',
  ]);
  assert.equal(plan.uploads.includes('android-release/manifest.json'), false);
  assert.equal(plan.uploads.includes('desktop-win/manifest.json'), false);
  assert.equal(plan.uploads.length, 10);
  assert.deepEqual(new Set(plan.manifestAssets.map(asset => `${asset.platform}:${asset.arch}`)), new Set(['android:any', 'win32:x64', 'darwin:arm64', 'linux:x64']));
  assert.equal(plan.manifestAssets.find(asset => asset.platform === 'android').file, 'android-release/midori-wallet-android-v1.0.6.apk');
  assert.equal(plan.manifestAssets.find(asset => asset.platform === 'win32').file, 'desktop-win/squirrel.windows/x64/Midori Wallet Setup.exe');
  assert.equal(plan.manifestAssets.find(asset => asset.platform === 'darwin').file, 'desktop-mac/Midori-Wallet-1.0.4-arm64.dmg');
  assert.equal(plan.manifestAssets.find(asset => asset.platform === 'linux').file, 'desktop-linux/deb/x64/midori-wallet_1.0.4_amd64.deb');
});

test('release asset planning fails fast on a missing platform installer or a duplicate', () => {
  assert.throws(() => planReleaseAssets(['android-release/midori-wallet-android-v1.0.6.apk']), /Missing release installer for: darwin:arm64, linux:x64, win32:x64/);
  assert.throws(() => planReleaseAssets([
    'a/Midori Wallet Setup.exe', 'b/Midori Wallet Setup.exe',
    'android-release/midori-wallet-android-v1.0.6.apk',
    'desktop-mac/Midori-Wallet-1.0.4-arm64.dmg',
    'desktop-linux/midori-wallet_1.0.4_amd64.deb',
  ]), /Duplicate primary asset for win32:x64/);
});
