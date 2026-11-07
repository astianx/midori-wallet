import { createHmac, timingSafeEqual } from 'node:crypto';
import { ApiError } from './errors.js';

// Hand-rolled REST client instead of @transak/wdk-protocol-fiat-transak: that
// package requires @tetherto/wdk-wallet as a peer (a whole account/signing
// abstraction this server doesn't use; see server/lib/payment-provider.js,
// which already talks to its own providers over plain fetch the same way).
const HOSTS = {
  STAGING: { api: 'https://api-stg.transak.com', gateway: 'https://api-gateway-stg.transak.com' },
  PRODUCTION: { api: 'https://api.transak.com', gateway: 'https://api-gateway.transak.com' },
};
const unavailable = () => new ApiError(502, 'RAMP_PROVIDER_UNAVAILABLE', 'The buy/sell provider could not confirm the request');

export function transakClient(config, fetchImpl = fetch) {
  const hosts = HOSTS[config.transakEnvironment];
  let cached; // { token, exp } — exp is a Unix-seconds timestamp from Transak
  let refreshing;
  const request = async (url, init) => {
    let response;
    try { response = await fetchImpl(url, { redirect: 'error', signal: AbortSignal.timeout(20000), ...init }); }
    catch { throw unavailable(); }
    const body = await response.json().catch(() => null);
    if (!response.ok || !body) throw unavailable();
    return body;
  };
  const refresh = async () => {
    const body = await request(`${hosts.api}/partners/api/v2/refresh-token`, {
      method: 'POST',
      headers: { 'x-api-key': config.transakApiKey, 'api-secret': config.transakApiSecret, 'content-type': 'application/json' },
      body: JSON.stringify({ apiKey: config.transakApiKey }),
    });
    if (typeof body.data?.accessToken !== 'string' || typeof body.data?.expiresAt !== 'number') throw unavailable();
    cached = { token: body.data.accessToken, exp: body.data.expiresAt };
    return cached;
  };
  // The access token is valid 7 days and is invalidated the moment a new one
  // is minted (Transak's own design; see docs.transak.com/api/public/
  // refresh-access-token). Caching here and only refreshing close to expiry
  // keeps that invalidation from racing against webhook verification, which
  // must HMAC against the very token that was active when Transak signed it.
  const accessToken = async () => {
    const now = Math.floor(Date.now() / 1000);
    if (cached && cached.exp > now + 60) return cached.token;
    refreshing ??= refresh().finally(() => { refreshing = undefined; });
    return (await refreshing).token;
  };
  return {
    async widgetSession(widgetParams, userIp) {
      const token = await accessToken();
      const body = await request(`${hosts.gateway}/api/v2/auth/session`, {
        method: 'POST',
        headers: { 'x-api-key': config.transakApiKey, 'access-token': token, 'content-type': 'application/json', 'x-user-ip': userIp },
        body: JSON.stringify({ widgetParams }),
      });
      if (typeof body.data?.widgetUrl !== 'string') throw unavailable();
      return body.data.widgetUrl;
    },
    async findOrderByPartnerOrderId(partnerOrderId) {
      const token = await accessToken();
      const url = new URL(`${hosts.api}/partners/api/v2/orders`);
      url.searchParams.set('filter[partnerOrderId]', partnerOrderId);
      url.searchParams.set('limit', '1');
      const body = await request(url, { headers: { 'x-api-key': config.transakApiKey, 'access-token': token } });
      const order = Array.isArray(body.data) ? body.data[0] : null;
      return order && typeof order === 'object' ? order : null;
    },
    // Verifies the webhook `data` field: Transak signs it as an HS256 JWT
    // using the Partner Access Token as the HMAC secret (see docs.transak.com
    // /guides/how-to-decrypt-webhook-payload). The header's declared "alg" is
    // never trusted — the signature is always recomputed as HMAC-SHA256,
    // which also rejects an attacker-supplied "none" algorithm.
    async verifyWebhook(token) {
      if (typeof token !== 'string') return null;
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const [headerPart, payloadPart, signaturePart] = parts;
      let provided;
      try { provided = Buffer.from(signaturePart, 'base64url'); } catch { return null; }
      const key = await accessToken();
      const expected = createHmac('sha256', key).update(`${headerPart}.${payloadPart}`).digest();
      if (provided.length !== expected.length || !timingSafeEqual(provided, expected)) return null;
      try { return JSON.parse(Buffer.from(payloadPart, 'base64url').toString('utf8')); } catch { return null; }
    },
  };
}
