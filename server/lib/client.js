import { sign, randomBytes } from 'node:crypto';
import { requestMessage } from './crypto.js';

export class MidoriClient {
  constructor({ origin, privateKey, session, fetchImpl = fetch }) {
    const base = new URL(origin);
    if (base.origin !== origin.replace(/\/$/,'') || base.username || base.password || (base.protocol !== 'https:' && !(base.protocol === 'http:' && ['localhost','127.0.0.1'].includes(base.hostname)))) throw new Error('Invalid API origin');
    Object.assign(this, { origin: base.origin, privateKey, session, fetchImpl });
  }

  async request(method, target, value) {
    if (typeof target !== 'string' || !target.startsWith('/api/v1/') || target.includes('#') || target.includes('..')) throw new Error('Invalid API target');
    const url = new URL(target, this.origin);
    if (url.origin !== this.origin || url.pathname !== target.split('?')[0] || /[\\\u0000-\u0020]/.test(target)) throw new Error('Invalid API target');
    const body = value === undefined ? '' : JSON.stringify(value);
    const headers = { 'Content-Type': 'application/json' };
    if (this.session) {
      const timestamp = String(Math.floor(Date.now() / 1000));
      const nonce = randomBytes(24).toString('base64url');
      headers.Authorization = `Bearer ${this.session.token}`;
      headers['X-Midori-Timestamp'] = timestamp;
      headers['X-Midori-Nonce'] = nonce;
      headers['X-Midori-Signature'] = sign(null, Buffer.from(requestMessage({ method, target, timestamp, nonce, token: this.session.token, body })), this.privateKey).toString('base64url');
    }
    const response = await this.fetchImpl(url, { method, headers, redirect: 'error', credentials: 'omit', cache: 'no-store', ...(body ? { body } : {}), signal: AbortSignal.timeout(15000) });
    const result = await response.json();
    if (!response.ok) {
      const error = new Error(result.error?.message || 'API request failed');
      Object.assign(error, { status: response.status, code: result.error?.code, details: result.error?.details });
      throw error;
    }
    return result;
  }
}
