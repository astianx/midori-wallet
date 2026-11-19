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
