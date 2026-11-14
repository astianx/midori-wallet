import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = process.env.MIDORI_FONT_OUTPUT || path.join(root, 'public/fonts');
const catalogRoot = process.env.MIDORI_LOCALE_ROOT || path.join(root, 'src/lib/i18n/messages');
const families = [
  ['km', 'Noto Sans Khmer', 'notosanskhmer'],
  ['th', 'Noto Sans Thai', 'notosansthai'],
  ['ja', 'Noto Sans JP', 'notosansjp'],
  ['ko', 'Noto Sans KR', 'notosanskr'],
  ['zh-cn', 'Noto Sans SC', 'notosanssc'],
];
const headers = { 'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36' };
const download = async url => {
  const response = await fetch(url, { headers, signal: AbortSignal.timeout(30000) });
  if (!response.ok) throw new Error(`Font download returned HTTP ${response.status}`);
  return response;
};
const manifest = { fonts: [] };
await mkdir(output, { recursive: true });

for (const [locale, family, repository] of families) {
  const messages = JSON.parse(await readFile(path.join(catalogRoot, `${locale}.json`), 'utf8'));
  const characters = [...new Set(Object.values(messages).join(''))].sort().join('');
  const query = new URLSearchParams({ family: `${family}:wght@400..700`, display: 'swap', text: characters });
  const css = await (await download(`https://fonts.googleapis.com/css2?${query}`)).text();
  const urls = [...new Set([...css.matchAll(/src:\s*url\((https:[^)]+)\)/g)].map(match => match[1]))];
  if (urls.length !== 1 || new URL(urls[0]).hostname !== 'fonts.gstatic.com') throw new Error(`Unexpected font response for ${locale}`);
  const bytes = Buffer.from(await (await download(urls[0])).arrayBuffer());
  if (bytes.subarray(0, 4).toString() !== 'wOF2') throw new Error(`Expected WOFF2 for ${locale}`);
  const file = `noto-${locale}-ui.woff2`;
  const license = `noto-${locale}-OFL.txt`;
  const licenseText = await (await download(`https://raw.githubusercontent.com/google/fonts/main/ofl/${repository}/OFL.txt`)).text();
  if (!licenseText.includes('SIL OPEN FONT LICENSE')) throw new Error(`Font license missing for ${locale}`);
  await writeFile(path.join(output, file), bytes);
  await writeFile(path.join(output, license), licenseText);
  manifest.fonts.push({ locale, family, file, license, characters, sha256: createHash('sha256').update(bytes).digest('hex'), bytes: bytes.length });
  console.log(`${locale}: ${bytes.length} bytes, ${[...characters].length} interface characters`);
}
await writeFile(path.join(output, 'coverage.json'), JSON.stringify(manifest, null, 2) + '\n');
