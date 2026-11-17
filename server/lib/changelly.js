import { createHash, createPrivateKey, createPublicKey, randomUUID, sign } from 'node:crypto';
import { formatUnits, parseUnits } from 'ethers';
import { ApiError } from './errors.js';

const unavailable = () => new ApiError(502, 'EXCHANGE_PROVIDER_UNAVAILABLE', 'Changelly could not confirm the request');
export const exchangeDecimal = value => typeof value === 'string' && /^(0|[1-9][0-9]{0,17})(\.[0-9]{1,36})?$/.test(value);
export function changellyCredentials(secret, apiKey) {
  try {
    if (typeof secret !== 'string' || !/^(?:[0-9a-fA-F]{2})+$/.test(secret)) throw new Error();
    const key = createPrivateKey({ key: Buffer.from(secret, 'hex'), format: 'der', type: 'pkcs8' });
    if (key.asymmetricKeyType !== 'rsa' || key.asymmetricKeyDetails.modulusLength < 2048) throw new Error();
    const derived = createHash('sha256').update(createPublicKey(key).export({ type: 'pkcs1', format: 'der' })).digest('base64');
    return { key, apiKey: apiKey || derived };
  } catch { throw new Error('Invalid CHANGELLY_API_SECRET: expected a hex-encoded PKCS8 RSA private key'); }
}

export function changellyClient(config, fetchImpl = fetch) {
  let credentials, cached, loading;
  const rpc = async (method, params) => {
    credentials ??= changellyCredentials(config.changellyApiSecret, config.changellyApiKey);
    const id = randomUUID(), body = JSON.stringify({ jsonrpc: '2.0', id, method, params });
    let response;
    try {
      response = await fetchImpl('https://api.changelly.com/v2', {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20000),
        headers: { 'content-type': 'application/json', 'X-Api-Key': credentials.apiKey, 'X-Api-Signature': sign('sha256', Buffer.from(body), credentials.key).toString('base64') }, body,
      });
    } catch { throw unavailable(); }
    const data = await response.json().catch(() => null);
    if (!response.ok || !data || data.id !== id || data.jsonrpc !== '2.0' || data.error || !Object.hasOwn(data, 'result')) throw unavailable();
    return data.result;
  };
  return {
    async currencies() {
      if (cached && Date.now() - cached.at < 30000) return cached.currencies;
      loading ??= rpc('getCurrenciesFull', {}).then(result => {
        if (!Array.isArray(result)) throw unavailable();
        const currencies = result.filter(item => typeof item.ticker === 'string' && /^[a-z0-9]{1,32}$/.test(item.ticker) && item.enabled === true).map(item => ({
          ticker: item.ticker, name: item.fullName || item.fullname || item.name || item.ticker.toUpperCase(),
          network: String(item.network || item.blockchain || ''), contract: item.contractAddress?.toLowerCase() || null,
          decimals: item.blockchainPrecision, buy: item.enabledTo === true, sell: item.enabledFrom === true,
          extraIdName: item.extraIdName || null, notification: item.notifications?.payin || null,
        }));
        cached = { at: Date.now(), currencies }; return currencies;
      }).finally(() => { loading = undefined; });
      return loading;
    },
    async estimate(from, to, amount) {
      const result = await rpc('getExchangeAmount', [{ from, to, amountFrom: amount }]);
      const item = Array.isArray(result) && result.find(item => item.from === from && item.to === to);
      if (!item) throw new ApiError(409, 'EXCHANGE_PAIR_UNAVAILABLE', 'This currency pair or amount is unavailable');
      if (!exchangeDecimal(item.amountTo) || !exchangeDecimal(item.networkFee)) throw unavailable();
      const net = parseUnits(item.amountTo, 36) - parseUnits(item.networkFee, 36);
      if (net <= 0n) throw new ApiError(409, 'EXCHANGE_AMOUNT_UNAVAILABLE', 'The amount does not cover the payout network cost');
      return { amountTo: formatUnits(net, 36), networkFee: formatUnits(parseUnits(item.networkFee, 36), 36),
        fee: exchangeDecimal(item.fee) ? formatUnits(parseUnits(item.fee, 36), 36) : null,
        min: exchangeDecimal(item.minFrom ?? item.min) ? item.minFrom ?? item.min : null,
        max: exchangeDecimal(item.maxFrom ?? item.max) ? item.maxFrom ?? item.max : null };
    },
    async validate(currency, address, extraId) {
      const result = await rpc('validateAddress', { currency, address, ...(extraId ? { extraId } : {}) });
      return result?.result === true;
    },
    create(params) { return rpc('createTransaction', params); },
    async transaction(id) {
      const result = await rpc('getTransactions', { id });
      if (!Array.isArray(result) || result.length !== 1 || result[0].id !== id) throw unavailable();
      return result[0];
    },
  };
}
