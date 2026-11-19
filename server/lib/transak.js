import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { parseUnits, formatUnits } from 'ethers';
import TransakProtocol from '@transak/wdk-protocol-fiat-transak';
import { ApiError, requireValue } from './errors.js';

const HOSTS = {
  STAGING: { api: 'https://api-stg.transak.com', gateway: 'https://api-gateway-stg.transak.com', widget: 'https://global-stg.transak.com' },
  PRODUCTION: { api: 'https://api.transak.com', gateway: 'https://api-gateway.transak.com', widget: 'https://global.transak.com' },
};
const unavailable = () => new ApiError(502, 'RAMP_PROVIDER_UNAVAILABLE', 'The buy/sell provider could not confirm the request');
const atomic = (value, decimals) => {
  try {
    const result = parseUnits(String(value), decimals);
    requireValue(result > 0n && result < 2n ** 256n, 'Invalid amount');
    return result;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(400, 'INVALID_REQUEST', 'Invalid or excessively precise amount');
  }
};

export function transakClient(config, fetchImpl = fetch, db) {
  const hosts = HOSTS[config.transakEnvironment];
  let cached;
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
    if (typeof body.data?.accessToken !== 'string' || !body.data.accessToken || !Number.isSafeInteger(body.data?.expiresAt) || body.data.expiresAt <= Math.floor(Date.now() / 1000) + 60) throw unavailable();
    return { token: body.data.accessToken, exp: body.data.expiresAt };
  };
  const accessToken = async () => {
    const now = Math.floor(Date.now() / 1000);
    if (db) {
      // Refresh invalidates the previous token; API and workers must share it.
      return db.transaction(async connection => {
        await connection.query("SET LOCAL idle_in_transaction_session_timeout='30s'");
        await connection.query("SET LOCAL statement_timeout='30s'");
        await connection.query('SELECT pg_advisory_xact_lock(738194261)');
        const keyHash = createHash('sha256').update(config.transakApiKey).digest('hex');
        const { rows } = await connection.query('SELECT access_token,expires_at FROM transak_tokens WHERE environment=$1 AND api_key_hash=$2', [config.transakEnvironment, keyHash]);
        if (rows[0] && Number(rows[0].expires_at) > now + 60) return rows[0].access_token;
        const issued = await refresh();
        await connection.query(`INSERT INTO transak_tokens(environment,api_key_hash,access_token,expires_at) VALUES($1,$2,$3,$4)
          ON CONFLICT(environment) DO UPDATE SET api_key_hash=$2,access_token=$3,expires_at=$4`, [config.transakEnvironment, keyHash, issued.token, issued.exp]);
        return issued.token;
      });
    }
    if (cached && cached.exp > now + 60) return cached.token;
    refreshing ??= refresh().then(value => (cached = value)).finally(() => { refreshing = undefined; });
    return (await refreshing).token;
  };
  const widgetSession = async (widgetParams, userIp) => {
    const token = await accessToken();
    const body = await request(`${hosts.gateway}/api/v2/auth/session`, {
      method: 'POST',
      headers: { 'x-api-key': config.transakApiKey, 'access-token': token, 'content-type': 'application/json', 'x-user-ip': userIp },
      body: JSON.stringify({ widgetParams }),
    });
    let url;
    try { url = new URL(body.data?.widgetUrl); } catch { throw unavailable(); }
    if (url.origin !== hosts.widget || url.username || url.password || !url.searchParams.get('sessionId')) throw unavailable();
    return url.href;
  };
  const protocol = (userIp, validate = () => {}) => new TransakProtocol(undefined, {
    apiKey: config.transakApiKey, environment: config.transakEnvironment,
    widgetUrl: params => { validate(params); return widgetSession(params, userIp); },
    getOrder: async id => {
      const token = await accessToken();
      const body = await request(`${hosts.api}/partners/api/v2/order/${encodeURIComponent(id)}`, { headers: { 'x-api-key': config.transakApiKey, 'access-token': token } });
      if (!body.data || typeof body.data.status !== 'string') throw unavailable();
      return body.data;
    },
  });
  const coverageProtocol = protocol();
  let partnerCache;
  let partnerRequest;
  const products = async () => {
    if (partnerCache && Date.now() - partnerCache.at < 30000) return partnerCache.products;
    partnerRequest ??= request(`${hosts.api}/partners/api/v2/${encodeURIComponent(config.transakApiKey)}`, { headers: { 'x-api-key': config.transakApiKey } }).then(body => {
      const partner = body.data ?? body.response ?? body;
      if (!Array.isArray(partner.productsAvailed) || partner.productsAvailed.some(product => typeof product !== 'string')) throw unavailable();
      partnerCache = { at: Date.now(), products: partner.productsAvailed.filter(product => ['BUY','SELL'].includes(product)) };
      return partnerCache.products;
    }).finally(() => { partnerRequest = undefined; });
    return partnerRequest;
  };
  const coverage = async sdk => {
    try {
      const [assets, currencies] = await Promise.all([sdk.getSupportedCryptoAssets(), sdk.getSupportedFiatCurrencies()]);
      return { assets: assets.filter(asset => (asset.networkCode === 'base' && ['USDT', 'USDC'].includes(asset.code)) || (asset.code === 'BTC' && asset.networkCode === 'mainnet' && asset.decimals === 8)), currencies };
    } catch { throw unavailable(); }
  };
  return {
    widgetSession,
    async options() {
      const [{ assets, currencies }, enabledProducts] = await Promise.all([coverage(coverageProtocol), products()]);
      return {
        environment: config.transakEnvironment, network: 'base', products: enabledProducts,
        assets: assets.map(asset => ({ symbol: asset.code, network: asset.code === 'BTC' ? 'bitcoin' : 'base', decimals: asset.decimals, roundOff: asset.metadata.roundOff, chainId: String(asset.metadata.network.chainId || ''), buy: enabledProducts.includes('BUY') && asset.metadata.isAllowed === true, sell: enabledProducts.includes('SELL') && asset.metadata.isPayInAllowed === true })),
        fiatCurrencies: currencies.map(fiat => ({ code: fiat.code, decimals: fiat.decimals, buy: enabledProducts.includes('BUY') && fiat.metadata.isAllowed === true, sell: enabledProducts.includes('SELL') && fiat.metadata.isPayOutAllowed === true })),
      };
    },
    async createWidget(order, userIp) {
      requireValue((await products()).includes(order.kind.toUpperCase()), 'This product is not enabled for the Transak API key');
      let expected;
      const sdk = protocol(userIp, params => requireValue((params.fiatAmount ?? params.cryptoAmount) === expected, 'Amount exceeds provider precision'));
      const { assets, currencies } = await coverage(sdk);
      const asset = assets.find(item => item.code === order.crypto_currency);
      const fiat = currencies.find(item => item.code === order.fiat_currency);
      requireValue(asset && fiat, 'Unsupported currency or network');
      requireValue(order.kind === 'buy' ? asset.metadata.isAllowed === true && fiat.metadata.isAllowed === true : asset.metadata.isPayInAllowed === true && fiat.metadata.isPayOutAllowed === true, 'This buy/sell pair is unavailable');
      if (asset.code === 'BTC') requireValue(asset.networkCode === 'mainnet' && asset.decimals === 8, 'Unsupported Bitcoin network');
      else if (config.transakEnvironment === 'PRODUCTION') requireValue(String(asset.metadata.network.chainId) === '8453' && asset.decimals === 6, 'Unsupported asset network');
      const options = {
        cryptoAsset: asset.code, fiatCurrency: fiat.code,
        ...(order.fiat_amount != null ? { fiatAmount: atomic(order.fiat_amount, fiat.decimals) } : { cryptoAmount: atomic(order.crypto_amount, asset.decimals) }),
        ...(order.kind === 'buy' ? { recipient: order.wallet_address } : {}),
        config: {
          network: asset.networkCode, referrerDomain: config.transakReferrerDomain, partnerOrderId: order.id,
          ...(order.kind === 'buy' ? { disableWalletAddressForm: true } : {}),
          ...(order.payment_method ? { paymentMethod: order.payment_method } : {}),
        },
      };
      expected = Number(formatUnits(options.fiatAmount ?? options.cryptoAmount, options.fiatAmount !== undefined ? fiat.decimals : asset.decimals));
      try {
        const result = await sdk[order.kind](options);
        return result.buyUrl ?? result.sellUrl;
      } catch (error) {
        if (error instanceof ApiError) throw error;
        throw unavailable();
      }
    },
    async findOrderByPartnerOrderId(partnerOrderId, kind) {
      const token = await accessToken();
      const url = new URL(`${hosts.api}/partners/api/v2/orders`);
      url.searchParams.set('filter[partnerOrderId]', partnerOrderId);
      url.searchParams.set('filter[productsAvailed]', JSON.stringify([kind.toUpperCase()]));
      url.searchParams.set('limit', '1');
      const body = await request(url, { headers: { 'x-api-key': config.transakApiKey, 'access-token': token } });
      const order = Array.isArray(body.data) ? body.data.find(item => item.partnerOrderId === partnerOrderId && item.isBuyOrSell === kind.toUpperCase()) : null;
      const id = order?.id || order?._id;
      return id ? (await coverageProtocol.getTransactionDetail(id)).metadata : null;
    },
    async verifyWebhook(token) {
      if (typeof token !== 'string' || token.length > 200000) return null;
      const parts = token.split('.');
      if (parts.length !== 3 || parts.some(part => !/^[A-Za-z0-9_-]+$/.test(part))) return null;
      const [headerPart, payloadPart, signaturePart] = parts;
      let header, claims;
      try {
        header = JSON.parse(Buffer.from(headerPart, 'base64url').toString('utf8'));
        claims = JSON.parse(Buffer.from(payloadPart, 'base64url').toString('utf8'));
      } catch { return null; }
      const now = Math.floor(Date.now() / 1000);
      if (header?.alg !== 'HS256' || !claims || typeof claims !== 'object' || Array.isArray(claims)) return null;
      if (claims.exp !== undefined && (typeof claims.exp !== 'number' || claims.exp <= now)) return null;
      if (claims.nbf !== undefined && (typeof claims.nbf !== 'number' || claims.nbf > now)) return null;
      const key = await accessToken();
      const provided = Buffer.from(signaturePart, 'base64url');
      const expected = createHmac('sha256', key).update(`${headerPart}.${payloadPart}`).digest();
      return provided.length === expected.length && timingSafeEqual(provided, expected) ? claims : null;
    },
  };
}
