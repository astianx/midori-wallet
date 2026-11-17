import eventBus from '../eventBus.js';
// Only a public account address and a verification outcome survive the native
// picker pause. Neither file contents nor credentials enter navigation storage.
const outcomes = new Map();
export function publishRecoveryResult(address, result) {
  if (!address) return;
  outcomes.clear(); outcomes.set(address, result);
  eventBus.emit('recoveryFileResult', address);
  setTimeout(() => { if (outcomes.get(address) === result) outcomes.delete(address); }, 120000);
}
export function takeRecoveryResult(address) { const result = outcomes.get(address); outcomes.delete(address); return result; }
export function nativeRecoveryFiles(win = window) {
  if (import.meta.env.VITE_BUILD_TYPE === 'electron') {
    const files = win.electron?.recoveryFiles;
    const failure = error => { if (/cancelled|canceled/i.test(error.message || '')) throw Object.assign(new Error('File picker cancelled'), { name: 'AbortError' }); throw error; };
    return files && { save: text => files.save(text).catch(failure), read: () => files.read().catch(failure) };
  }
  if (import.meta.env.VITE_BUILD_TYPE === 'phonegap') {
    const call = (action, value) => new Promise((resolve, reject) => win.cordova.exec(resolve, message => reject(Object.assign(new Error('Recovery file could not be opened or saved.'), { name: /cancel/i.test(String(message)) ? 'AbortError' : 'Error' })), 'MidoriVault', action, value === undefined ? [] : [value]));
    return { save: text => call('saveFile', text), read: () => call('readFile') };
  }
}
export async function saveRecoveryFile(descriptor) {
  const text = JSON.stringify(descriptor, null, 2);
  const native = nativeRecoveryFiles();
  if (native) return native.save(text);
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const link = document.createElement('a'); link.href = url; link.download = 'midori-recovery.json'; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
