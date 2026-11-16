import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';

const repository = fileURLToPath(new URL('../../', import.meta.url));
export async function sha256File(file) {
  const hash = createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

function filesIn(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? filesIn(file) : entry.isFile() ? [file] : [];
  });
}

export async function collectArtifacts(target, { root = repository, platform = process.platform, env = process.env } = {}) {
  const release = 'phonegap/build-native/release/platforms/android/app/build/outputs';
  const targets = {
    web: { package: 'web', source: 'server/dist', required: ['index.html'] },
    desktop: {
      package: 'electron', source: 'electron/out/make',
      required: platform === 'darwin' ? ['.zip', '.dmg'] : platform === 'win32' ? ['.exe', '.nupkg', 'RELEASES'] : env.MIDORI_DESKTOP_FORMAT === 'zip' ? ['.zip'] : ['.zip', '.deb'],
    },
    android: { package: 'phonegap', source: 'phonegap/build-native/debug/platforms/android/app/build/outputs/apk/debug', required: ['app-debug.apk'] },
    'android-release': { package: 'phonegap', files: [`${release}/apk/release/app-release.apk`, `${release}/bundle/release/app-release.aab`], required: ['.apk', '.aab'] },
    ios: { package: 'phonegap', files: ['artifacts/midori-ios-simulator.tar.gz'], required: ['.tar.gz'] },
  };
  const specification = targets[target];
  if (!specification) throw new Error('Unknown artifact target');
  const output = path.join(root, 'artifacts', target);
  fs.rmSync(output, { recursive: true, force: true });
  fs.mkdirSync(output, { recursive: true });
  const pkg = JSON.parse(fs.readFileSync(path.join(root, specification.package, 'package.json'), 'utf8'));
  const source = specification.source && path.join(root, specification.source);
  let files = source ? filesIn(source) : specification.files.map(file => path.join(root, file)).filter(file => fs.existsSync(file));
  if (target === 'desktop') files = files.filter(file => /\.(zip|dmg|deb|exe|nupkg)$/.test(file) || path.basename(file) === 'RELEASES');
  const records = [];
  for (const file of files.sort()) {
    const name = source ? path.relative(source, file) : target === 'android-release' ? `midori-wallet-android-v${pkg.version}${path.extname(file)}` : path.basename(file);
    const destination = path.join(output, name);
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    fs.copyFileSync(file, destination);
    records.push({ path: name.split(path.sep).join('/'), bytes: fs.statSync(destination).size, sha256: await sha256File(destination) });
  }
  const missing = specification.required.filter(suffix => !records.some(file => file.bytes > 0 && file.path.endsWith(suffix)));
  const manifest = {
    schemaVersion: 1, product: 'Midori Wallet', target, platform,
    profile: target === 'android-release' ? 'release' : env.MIDORI_BUILD_PROFILE || 'debug',
    version: pkg.version, commit: env.GITHUB_SHA || env.VITE_COMMIT || 'local',
    runId: env.GITHUB_RUN_ID || null, runAttempt: env.GITHUB_RUN_ATTEMPT || null,
    node: process.version, complete: missing.length === 0, missing, files: records,
  };
  if (target === 'android-release') {
    const verification = JSON.parse(fs.readFileSync(path.join(root, 'artifacts/android-signatures.json'), 'utf8'));
    if (records.length !== 2 || records.some(file => verification.files[path.extname(file.path)] !== file.sha256)) throw new Error('Signed Android artifacts differ from the verified APK/AAB');
    manifest.signing = { certificateSha256: verification.certificateSha256, apkVerified: true, aabVerified: true };
  }
  fs.writeFileSync(path.join(output, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  if (env.GITHUB_STEP_SUMMARY) fs.appendFileSync(env.GITHUB_STEP_SUMMARY, `\n## ${target} artifacts\n\nVersion: ${pkg.version}; profile: ${manifest.profile}; complete: ${manifest.complete}.\n\n${records.map(file => `- ${file.path} (${file.bytes} bytes), SHA-256: ${file.sha256}`).join('\n')}\n${missing.length ? `\nMissing: ${missing.join(', ')}\n` : ''}`);
  return manifest;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const manifest = await collectArtifacts(process.argv[2]);
  console.log(JSON.stringify({ target: manifest.target, complete: manifest.complete, files: manifest.files.length, missing: manifest.missing }));
  if (!manifest.complete) process.exitCode = 1;
}
