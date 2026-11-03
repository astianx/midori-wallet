const encoder = new TextEncoder();
const decoder = new TextDecoder();
const encode = bytes => btoa(String.fromCharCode(...bytes));
const decode = text => Uint8Array.from(atob(text), char => char.charCodeAt(0));
const aad = encoder.encode('midori.platform-protection/v1');
export async function wrappingKey() {
  const db = await new Promise((resolve, reject) => {
    const request = indexedDB.open('midori-secure-vault', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('keys');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(new Error('Secure local storage could not be opened'));
  });
  try {
    const existing = await new Promise((resolve, reject) => {
      const transaction = db.transaction('keys', 'readonly');
      const request = transaction.objectStore('keys').get('wrapping-v1');
      transaction.oncomplete = () => resolve(request.result);
      transaction.onerror = () => reject(new Error('Secure local storage could not be read'));
    });
    if (existing) return existing;
    const candidate = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
    return await new Promise((resolve, reject) => {
      const transaction = db.transaction('keys', 'readwrite');
      const store = transaction.objectStore('keys');
      const request = store.get('wrapping-v1');
      let selected;
      request.onsuccess = () => { selected = request.result || candidate; if (!request.result) store.put(candidate, 'wrapping-v1'); };
      transaction.oncomplete = () => resolve(selected);
      transaction.onerror = () => reject(new Error('Secure local storage could not be written'));
    });
  } finally { db.close(); }
}

export function platformProtection({ buildType = import.meta.env?.VITE_BUILD_TYPE || 'web', window: win = globalThis.window } = {}) {
  if (buildType === 'electron') {
    if (!win?.electron?.vault) throw new Error('Protected desktop storage is unavailable');
    return win.electron.vault;
  }
  if (buildType === 'phonegap') {
    if (!win?.cordova?.exec) throw new Error('Protected mobile storage is unavailable');
    const call = (action, value) => new Promise((resolve, reject) => win.cordova.exec(resolve, () => reject(new Error('Protected mobile storage is unavailable')), 'MidoriVault', action, [value]));
    return { seal: value => call('seal', value), open: value => call('open', value), remove: value => call('remove', value) };
  }
  return {
    async seal(value) {
      const iv = crypto.getRandomValues(new Uint8Array(12));
      const cipher = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: aad }, await wrappingKey(), encoder.encode(value));
      return JSON.stringify({ type: 'web-cryptokey-v1', iv: encode(iv), ciphertext: encode(new Uint8Array(cipher)) });
    },
    async open(value) {
      const record = JSON.parse(value);
      if (record.type !== 'web-cryptokey-v1') throw new Error('Unexpected storage protection');
      const bytes = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv: decode(record.iv), additionalData: aad }, await wrappingKey(), decode(record.ciphertext)));
      try { return decoder.decode(bytes); } finally { bytes.fill(0); }
    },
  };
}
