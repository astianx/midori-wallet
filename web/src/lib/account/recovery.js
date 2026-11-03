import { generateMnemonic, mnemonicToSeed, validateMnemonic } from '@scure/bip39';
import { wordlist } from '@scure/bip39/wordlists/english';
import { AccountAdapter } from './AccountAdapter.js';
const drafts = new Map();
export async function beginRecovery(phrase, descriptor) {
  const mnemonic = phrase || generateMnemonic(wordlist, 128);
  if (!validateMnemonic(mnemonic, wordlist)) throw new Error('Invalid passphrase');
  const seed = await mnemonicToSeed(mnemonic);
  const adapter = new AccountAdapter();
  try {
    const account = descriptor ? await adapter.restore(seed, descriptor) : await adapter.create(seed);
    const handle = crypto.randomUUID();
    drafts.set(handle, { seed, phrase: mnemonic, descriptor: account, wordsVerified: !!phrase });
    return handle;
  } catch (error) { seed.fill(0); throw error; }
  finally { adapter.lock(); }
}
function draft(handle) { const value = drafts.get(handle); if (!value) throw new Error('Recovery expired. Start again.'); return value; }
export function recoveryPhrase(handle) { return draft(handle).phrase; }
export function recoveryDescriptor(handle) { return draft(handle).descriptor; }
export function verifyWords(handle, words) {
  const value = draft(handle);
  value.wordsVerified = words.every(({ index, word }) => value.phrase.split(' ')[index] === word.trim());
  return value.wordsVerified;
}
export function verifyDescriptor(descriptor, text) {
  const equal = (a, b) => a && b && typeof a === 'object' && typeof b === 'object' && Object.keys(a).length === Object.keys(b).length && Object.keys(a).every(key => typeof a[key] === 'object' ? equal(a[key], b[key]) : a[key] === b[key]);
  if (!equal(JSON.parse(text), descriptor)) throw new Error('The recovery file does not match this account.');
}
export async function consumeRecovery(handle, callback) {
  const value = draft(handle);
  if (!value.wordsVerified) throw new Error('Verify your passphrase first.');
  const result = await callback(value.seed, value.descriptor);
  value.seed.fill(0); value.phrase = ''; drafts.delete(handle);
  return result;
}
export function clearRecovery() { for (const value of drafts.values()) { value.seed.fill(0); value.phrase = ''; } drafts.clear(); }
