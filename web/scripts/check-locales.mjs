import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { baseCompile } from '@intlify/message-compiler';
import { languages } from '../src/lib/i18n/languages.js';

const directory = new URL('../src/lib/i18n/messages/', import.meta.url);
const reference = JSON.parse(await readFile(new URL('en.json', directory), 'utf8'));
const keys = Object.keys(reference).sort();
const fontDirectory = new URL('../public/fonts/', import.meta.url);
const fontCoverage = JSON.parse(await readFile(new URL('coverage.json', fontDirectory), 'utf8'));
const variables = message => [...message.matchAll(/\{([A-Za-z_]\w*)\}/g)].map(match => match[1]).sort();
const failures = [];

for (const { value: locale } of languages) {
  try {
    const messages = JSON.parse(await readFile(new URL(`${locale}.json`, directory), 'utf8'));
    assert.deepEqual(Object.keys(messages).sort(), keys, 'Message keys do not match English');
    const font = fontCoverage.fonts.find(font => font.locale === locale);
    if (font) {
      const bytes = await readFile(new URL(font.file, fontDirectory));
      assert.equal(createHash('sha256').update(bytes).digest('hex'), font.sha256, 'Font does not match its coverage manifest');
      const needsFont = {
        km: /[\u1780-\u17ff\u19e0-\u19ff]/u,
        th: /[\u0e00-\u0e7f]/u,
        ja: /[\u3000-\u30ff\u4e00-\u9fff\uff00-\uffef]/u,
        ko: /[\u1100-\u11ff\u3130-\u318f\uac00-\ud7af\u4e00-\u9fff]/u,
        'zh-cn': /[\u3000-\u30ff\u4e00-\u9fff\uff00-\uffef]/u,
      }[locale];
      const missingGlyphs = [...new Set(Object.values(messages).join(''))].filter(character => needsFont.test(character) && !font.characters.includes(character));
      assert.equal(missingGlyphs.length, 0, `Font subset is missing ${missingGlyphs.join('')}; run npm --prefix web run fonts:update`);
    }
    for (const key of keys) {
      assert.equal(typeof messages[key], 'string', `${key}: translation must be text`);
      assert.ok(messages[key].trim(), `${key}: translation is empty`);
      assert.deepEqual(variables(messages[key]), variables(reference[key]), `${key}: interpolation variables changed`);
      baseCompile(messages[key], { onError(error) { throw new Error(`${key}: ${error.message}`); } });
    }
  } catch (error) {
    failures.push(`${locale}: ${error.message}`);
  }
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`Validated ${languages.length} languages with ${keys.length} messages each and matching interpolation variables.`);
}
