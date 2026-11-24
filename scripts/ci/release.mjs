// Turns the android-release and desktop-release job artifacts (already
// downloaded to disk by actions/download-artifact) into the inputs needed to
// publish a GitHub release: which files to upload, and which single
// "installer" file per platform/arch goes into the signed update manifest
// (see sign-update-manifest.mjs and packages/update-core/index.js).
//
// Usage (from CI, after downloading artifacts under ./artifacts/release):
//   node scripts/ci/release.mjs ./artifacts/release
//
// Prints a JSON object: { uploads: [relative paths...], manifestAssets: [{platform,arch,file}] }.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

function filesIn(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    return entry.isDirectory() ? filesIn(file) : entry.isFile() ? [file] : [];
  });
}

// Classifies a flat list of POSIX-style relative paths (as produced by the
// android-release/desktop-release artifacts, concatenated across platforms)
// into release uploads and the per-platform/arch asset used for the signed
// update manifest. Pure/testable: no filesystem access.
export function planReleaseAssets(relativePaths) {
  const uploads = [];
  const manifestAssets = [];
  const primary = (platform, arch, file) => {
    const key = `${platform}:${arch}`;
    if (manifestAssets.some(asset => `${asset.platform}:${asset.arch}` === key)) throw new Error(`Duplicate primary asset for ${key} (${file})`);
    manifestAssets.push({ platform, arch, file });
  };
  for (const file of relativePaths) {
    const name = path.posix.basename(file);
    if (name === 'manifest.json' || name === 'android-signatures.json') continue; // CI provenance, not release assets.
    uploads.push(file);
    if (/\.apk$/.test(name)) primary('android', 'any', file);
    else if (/\.dmg$/.test(name)) primary('darwin', 'arm64', file);
    else if (/\.deb$/.test(name)) primary('linux', 'x64', file);
    else if (/ Setup\.exe$/.test(name)) primary('win32', 'x64', file);
  }
  const missing = ['android:any', 'darwin:arm64', 'linux:x64', 'win32:x64'].filter(key => !manifestAssets.some(asset => `${asset.platform}:${asset.arch}` === key));
  if (missing.length) throw new Error(`Missing release installer for: ${missing.join(', ')}`);
  return { uploads: uploads.sort(), manifestAssets };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const root = process.argv[2];
  if (!root) throw new Error('Usage: node scripts/ci/release.mjs <artifacts-directory>');
  const relativePaths = filesIn(root).map(file => path.relative(root, file).split(path.sep).join('/'));
  const plan = planReleaseAssets(relativePaths);
  console.log(JSON.stringify({
    uploads: plan.uploads.map(file => path.join(root, file)),
    manifestAssets: plan.manifestAssets.map(asset => ({ ...asset, file: path.join(root, asset.file) })),
  }));
}
