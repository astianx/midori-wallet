import { prettyVersion } from './version.js';
import stringify from 'fast-json-stable-stringify';
import {
  Amount,
  utils,
} from './account/domain.js';

import { isRef, unref } from 'vue';

export function cryptoSubtitle(wallet) {
  const list = [wallet.crypto.name];
  if (wallet.crypto.type === 'token') {
    list.push(wallet.platform.name);
  }
  return list.join('\xa0• ');
}

export function cryptoSubtitleWithSymbol(wallet) {
  const list = [wallet.crypto.symbol];
  if (wallet.crypto.type === 'token') {
    list.push(wallet.platform.name);
  }
  return list.join('\xa0• ');
}

export function safeOpen(value) {
  let url;
  try { url = new URL(value); } catch { return false; }
  if (!['https:', 'mailto:'].includes(url.protocol) || url.username || url.password) return false;
  const win = window.open(url.href, '_blank', 'noopener,noreferrer');
  if (win) win.opener = null;
  return false;
}

export function cryptoToFiat(amount, price, decimals) {
  return utils.atomToFiat(amount.value, price, decimals || amount.decimals);
}

export function fiatToCrypto(fiat, price, decimal) {
  const value = utils.fiatToAtom(fiat, price, decimal);
  return new Amount(value, decimal);
}

export function roundCrypto(amount, price) {
  return utils.atomToRoundUnit(amount.value, amount.decimals, price);
}

export function measureText(text) {
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  ctx.font = '16px sans-serif';
  return ctx.measureText(text);
}

export function defineAppProperty(app, property, value) {
  Object.defineProperty(app.config.globalProperties, property, {
    enumerable: true,
    configurable: true,
    ...isRef(value) ? { get: () => unref(value) } : { value },
  });
}

export function objectsIsEqual(a, b) {
  return stringify(a) === stringify(b);
}

export function isValidEmail(email) {
  return /^[^@]+@\w+(\.\w+)+\w$/.test(email);
}

export function isValidUsername(username) {
  return typeof username === 'string' && /^@?[a-z0-9][a-z0-9-]{0,62}$/i.test(username.trim());
}

export function registerProtocolHandler(crypto, account) {
  if (import.meta.env.VITE_PLATFORM !== 'web') return;
  if (!navigator.registerProtocolHandler) return;
  if (crypto.scheme && !account.clientStorage.hasProtocolHandler(crypto.scheme)) {
    account.clientStorage.setProtocolHandler(crypto.scheme);
    const handler = new URL(`${import.meta.env.BASE_URL}bip21/%s`, import.meta.env.VITE_SITE_URL);
    try {
      navigator.registerProtocolHandler(crypto.scheme, handler, 'Midori Wallet');
    } catch (e) {
      try {
        navigator.registerProtocolHandler(`web+${crypto.scheme}`, handler, 'Midori Wallet');

      } catch (e) {}
    }
  }
}

export function deepFreeze(object) {
  const propNames = Reflect.ownKeys(object);
  for (const name of propNames) {
    const value = object[name];
    if ((value && typeof value === 'object') || typeof value === 'function') {
      deepFreeze(value);
    }
  }
  return Object.freeze(object);
}

export function getRandomInt(max) {
  return Math.floor(Math.random() * max);
}

export async function isQrScanAvailable() {
  if (import.meta.env.VITE_BUILD_TYPE === 'phonegap') {
    return true;
  }
  if (!navigator.mediaDevices?.enumerateDevices) {
    return false;
  }
  const devices = await navigator.mediaDevices.enumerateDevices();
  if (devices.every((device) => device.kind !== 'videoinput')) {
    return false;
  }
  return true;
}

export function chunks(array, chunkSize) {
  const chunks = [];
  for (let i = 0; i < array.length; i += chunkSize) {
    chunks.push(array.slice(i, i + chunkSize));
  }
  return chunks;
}

export function formatDuration(seconds, locale, style) {
  if (seconds <= 0) seconds = 1;
  const duration = {};
  const units = [
    ['days', 86400],
    ['hours', 3600],
    ['minutes', 60],
    ['seconds', 1],
  ];
  for (const [unit, size] of units) {
    const value = Math.floor(seconds / size);
    if (value || Object.keys(duration).length) {
      duration[unit] = value;
    }
    seconds %= size;
    if (Object.keys(duration).length === 2) break;
  }
  if (Intl.DurationFormat) {
    return new Intl.DurationFormat(locale, { style }).format(duration);
  }
  return Object.entries(duration)
    .map(([unit, value]) => value + unit[0])
    .join(' ');
}

export function dataUrlSize(dataUrl = '') {
  const base64 = dataUrl.slice(dataUrl.indexOf(',') + 1);
  const padding = base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0;
  return Math.floor((base64.length * 3) / 4) - padding;
}
