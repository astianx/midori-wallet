#!/usr/bin/env node
// Builds and Ed25519-signs the update manifest consumed by
// packages/update-core/index.js (verifyManifest). Not part of the running
// server or client bundles: this is a release-time tool, run from CI (or by
// hand) once the platform artifacts for a tag are built, before uploading
// them as GitHub Release assets.
//
// Usage:
//   node scripts/sign-update-manifest.mjs \
//     --tag v1.2.0 --channel stable --out dist/release \
//     --asset platform=android,arch=any,file=midori-wallet.apk \
//     --asset platform=win32,arch=x64,file=midori-wallet-Setup.exe \
//     ...
//
// Reads the Ed25519 private key (PKCS8 PEM) from UPDATE_SIGNING_PRIVATE_KEY
// (preferred, e.g. a GitHub Actions secret) or from --key-file.
//
// Writes "<out>/midori-updates-<channel>.json" and the matching ".sig" file
// (base64url signature), both meant to be uploaded as release assets named
// exactly that way, per update-core's expected asset names.
import { createHash, createPrivateKey, sign } from 'node:crypto';
import { readFileSync, writeFileSync, statSync } from 'node:fs';
import { basename } from 'node:path';
import { repository } from '../packages/update-core/index.js';

function parseArgs(argv) {
  const result = { assets: [] };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--asset') { result.assets.push(argv[++i]); continue; }
    if (arg.startsWith('--')) { result[arg.slice(2)] = argv[++i]; continue; }
    throw new Error(`Unexpected argument: ${arg}`);
  }
  return result;
}

function parseAsset(spec) {
  const fields = Object.fromEntries(spec.split(',').map(pair => {
    const [key, ...rest] = pair.split('=');
    return [key, rest.join('=')];
  }));
  if (!fields.platform || !fields.arch || !fields.file) throw new Error(`Invalid --asset "${spec}" (need platform=,arch=,file=)`);
  if (!['win32', 'darwin', 'linux', 'android', 'ios', 'web'].includes(fields.platform)) throw new Error(`Invalid asset platform: ${fields.platform}`);
  if (!['x64', 'arm64', 'any'].includes(fields.arch)) throw new Error(`Invalid asset arch: ${fields.arch}`);
  const { size } = statSync(fields.file);
  const sha256 = createHash('sha256').update(readFileSync(fields.file)).digest('hex');
  const name = basename(fields.file);
  return { platform: fields.platform, arch: fields.arch, sha256, size, name };
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const { tag, channel, out } = args;
  if (!tag || !/^v?(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})(?:-beta\.(0|[1-9][0-9]{0,8}))?$/.test(tag)) throw new Error('Pass a valid --tag (matching the git tag of the release)');
  if (!['stable', 'beta'].includes(channel)) throw new Error('Pass --channel stable or --channel beta');
  if ((channel === 'stable') === tag.includes('-beta.')) throw new Error('Tag and channel disagree on beta status');
  if (!out) throw new Error('Pass --out <directory> for the manifest and signature files');
  if (!args.assets.length) throw new Error('Pass at least one --asset platform=,arch=,file=');

  const privateKeyPem = process.env.UPDATE_SIGNING_PRIVATE_KEY || (args['key-file'] && readFileSync(args['key-file'], 'utf8'));
  if (!privateKeyPem) throw new Error('Set UPDATE_SIGNING_PRIVATE_KEY or pass --key-file');
  const privateKey = createPrivateKey(privateKeyPem);

  const assets = args.assets.map(parseAsset).map(({ platform, arch, sha256, size, name }) => ({
    platform, arch, sha256, size,
    url: `https://github.com/${repository}/releases/download/${tag}/${name}`,
  }));
  const seen = new Set();
  for (const asset of assets) {
    const key = `${asset.platform}:${asset.arch}`;
    if (seen.has(key)) throw new Error(`Duplicate platform/arch in assets: ${key}`);
    seen.add(key);
  }

  const manifest = { schema: 'midori.updates', version: 1, channel, release: tag, assets };
  const bytes = Buffer.from(JSON.stringify(manifest));
  if (bytes.length > 65536) throw new Error('Manifest too large');
  const signature = sign(null, bytes, privateKey).toString('base64url');

  const manifestPath = `${out}/midori-updates-${channel}.json`;
  const signaturePath = `${manifestPath}.sig`;
  writeFileSync(manifestPath, bytes);
  writeFileSync(signaturePath, signature);
  console.log(`Wrote ${manifestPath} and ${signaturePath} (${assets.length} asset${assets.length === 1 ? '' : 's'})`);
}

main();
