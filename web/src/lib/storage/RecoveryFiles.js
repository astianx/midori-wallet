export function nativeRecoveryFiles(win = window) {
  if (import.meta.env.VITE_BUILD_TYPE === 'electron') return win.electron?.recoveryFiles;
  if (import.meta.env.VITE_BUILD_TYPE === 'phonegap') {
    const call = (action, value) => new Promise((resolve, reject) => win.cordova.exec(resolve, () => reject(new Error('Recovery file could not be opened or saved.')), 'MidoriVault', action, value === undefined ? [] : [value]));
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
