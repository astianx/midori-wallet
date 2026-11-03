export const APP_ORIGIN = 'https://localhost';

export function isAppUrl(value) {
  try {
    const url = new URL(value);
    return url.origin === APP_ORIGIN && !url.username && !url.password;
  } catch { return false; }
}

export function assertCaller(event) {
  if (!event?.senderFrame || event.senderFrame !== event.sender?.mainFrame || !isAppUrl(event.senderFrame.url)) throw new Error('Untrusted IPC caller');
}

export function externalUrl(value) {
  try {
    const url = new URL(value);
    if (['https:', 'mailto:'].includes(url.protocol) && !url.username && !url.password && url.origin !== APP_ORIGIN) return url.href;
  } catch { /* invalid external URL */ }
  return null;
}

export function paymentLink(value) {
  if (typeof value !== 'string' || value.length > 2048 || /[\u0000-\u0020]/.test(value)) return null;
  if (/^0x[0-9a-fA-F]{40}$/.test(value)) return value;
  try {
    const url = new URL(value);
    if (!['ethereum:', 'midoriwallet:'].includes(url.protocol) || url.username || url.password || url.hash) return null;
    if (url.protocol === 'midoriwallet:') {
      if (url.hostname !== 'pay' || (url.pathname && url.pathname !== '/')) return null;
      const address = url.searchParams.get('address');
      const chain = url.searchParams.get('chainId');
      if (!/^0x[0-9a-fA-F]{40}$/.test(address || '') || chain !== '8453' || [...url.searchParams.keys()].some(key => !['address','chainId'].includes(key))) return null;
      return `ethereum:${address}@8453`;
    }
    if (!/^0x[0-9a-fA-F]{40}@8453(?:\/transfer)?$/.test(url.pathname)) return null;
    if ([...url.searchParams.keys()].some(key => !['address','uint256'].includes(key))) return null;
    if (url.searchParams.has('address') && !/^0x[0-9a-fA-F]{40}$/.test(url.searchParams.get('address'))) return null;
    if (url.searchParams.has('uint256') && !/^(0|[1-9][0-9]{0,77})$/.test(url.searchParams.get('uint256'))) return null;
    return url.href;
  } catch { return null; }
}
