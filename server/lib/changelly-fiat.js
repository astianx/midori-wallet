import { createPrivateKey, sign } from 'node:crypto';
import { ApiError } from './errors.js';

const origin = 'https://fiat-api.changelly.com';
export function fiatCredentials(secret, apiKey) {
  try {
    if (typeof secret !== 'string' || typeof apiKey !== 'string' || !/^[a-f0-9]{64}$/i.test(apiKey)) throw new Error();
    const text = secret.trim().replace(/\\n/g, '\n');
    const key = text.startsWith('-----BEGIN ') ? createPrivateKey(text) : /^[a-f0-9]+$/i.test(text) ? createPrivateKey({ key: Buffer.from(text, 'hex'), format: 'der', type: 'pkcs8' }) : createPrivateKey(Buffer.from(text, 'base64').toString('utf8'));
    if (key.asymmetricKeyType !== 'rsa' || key.asymmetricKeyDetails.modulusLength < 2048) throw new Error();
    return { key, apiKey };
  } catch { throw new Error('CHANGELLY_FIAT_API_SECRET must contain the complete RSA private key, including both parts supplied by Changelly'); }
}
export function changellyFiatClient(config, fetchImpl = fetch) {
  let credentials;
  const request = async (method, path, query = {}, data) => {
    credentials ??= fiatCredentials(config.changellyFiatApiSecret, config.changellyFiatApiKey);
    const url = new URL(`/v1/${path}`, origin);
    for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== '') url.searchParams.set(key, value);
    const body = JSON.stringify(data || {});
    let response;
    try { response = await fetchImpl(url.href, { method, redirect: 'error', signal: AbortSignal.timeout(20000), headers: { 'Content-Type': 'application/json', 'X-Api-Key': credentials.apiKey, 'X-Api-Signature': sign('sha256', Buffer.from(url.href + body), credentials.key).toString('base64') }, ...(method === 'POST' ? { body } : {}) }); }
    catch { throw new ApiError(503, 'RAMP_PROVIDER_UNAVAILABLE', 'Changelly Fiat is temporarily unavailable'); }
    const value = await response.json().catch(() => null);
    if (!response.ok || !value || value.errorType) throw new ApiError(response.status === 401 ? 503 : 502, response.status === 401 ? 'RAMP_CREDENTIALS_UNAVAILABLE' : 'RAMP_PROVIDER_UNAVAILABLE', 'Changelly Fiat could not confirm the request');
    return value;
  };
  return {
    countries: kind => request('GET', 'available-countries', { supportedFlow: kind }),
    currencies: kind => request('GET', 'currencies', { supportedFlow: kind }),
    offers: (kind, query) => request('GET', kind === 'sell' ? 'sell/offers' : 'offers', query),
    create: (kind, data) => request('POST', kind === 'sell' ? 'sell/orders' : 'orders', {}, data),
    orders: externalOrderId => request('GET', 'orders', { externalOrderId, limit: '2' }),
  };
}
// Only provider-owned HTTPS checkout origins are opened by the clients. No wildcard hosts.
export const fiatCheckoutOrigins = Object.freeze(['https://changelly.com','https://buy.moonpay.com','https://sell.moonpay.com','https://widget.paybis.com','https://app.topperpay.com']);
export function fiatCheckoutUrl(value, environment) {
  try {
    const url = new URL(value);
    if (url.username || url.password || !fiatCheckoutOrigins.includes(url.origin)) return null;
    const sandbox = url.origin === 'https://changelly.com' && /^\/(buy|sell)\/?$/.test(url.pathname);
    if (environment === 'STAGING' && !sandbox || environment === 'PRODUCTION' && (sandbox || url.hostname.includes('sandbox'))) return null;
    return url.href;
  } catch { return null; }
}
