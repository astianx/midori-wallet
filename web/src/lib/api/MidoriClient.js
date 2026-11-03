const encoder = new TextEncoder();
function base64url(bytes) {
  return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}
async function digest(value) {
  const bytes = new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value)));
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
}

export class MidoriApiError extends Error {
  constructor(message, { code, status, details } = {}) {
    super(message);
    Object.assign(this, { code, status, details });
  }
}

export class MidoriClient {
  constructor({ origin, session, signMessage, fetchImpl = globalThis.fetch.bind(globalThis) }) {
    const base = new URL(origin);
    if (base.origin !== origin.replace(/\/$/,'') || base.username || base.password || (base.protocol !== 'https:' && !(base.protocol === 'http:' && ['localhost','127.0.0.1','10.0.2.2'].includes(base.hostname)))) throw new Error('Invalid API origin');
    Object.assign(this, { origin: base.origin, session, signMessage, fetchImpl });
  }

  async request(method, target, value) {
    if (!target.startsWith('/api/v1/') || target.includes('#') || target.includes('..')) throw new Error('Invalid API target');
    const url = new URL(target, this.origin);
    if (url.origin !== this.origin || url.pathname !== target.split('?')[0] || /[\\\u0000-\u0020]/.test(target)) throw new Error('Invalid API target');
    const body = value === undefined ? '' : JSON.stringify(value);
    const headers = { 'Content-Type': 'application/json' };
    if (this.session) {
      const timestamp = String(Math.floor(Date.now() / 1000));
      const nonce = base64url(crypto.getRandomValues(new Uint8Array(24)));
      const message = `Midori request v1\n${method.toUpperCase()}\n${target}\n${timestamp}\n${nonce}\n${await digest(this.session.token)}\n${await digest(body)}`;
      headers.Authorization = `Bearer ${this.session.token}`;
      headers['X-Midori-Timestamp'] = timestamp;
      headers['X-Midori-Nonce'] = nonce;
      headers['X-Midori-Signature'] = base64url(await this.signMessage(encoder.encode(message)));
    }
    let response;
    try { response = await this.fetchImpl(new URL(target, this.origin), { method, headers, redirect: 'error', credentials: 'omit', cache: 'no-store', ...(body ? { body } : {}), signal: AbortSignal.timeout(15000) }); }
    catch { throw new MidoriApiError('No se pudo confirmar la respuesta del servidor. Vuelve a consultar antes de reintentar.', { code: 'NETWORK_UNCONFIRMED' }); }
    let result;
    try { result = await response.json(); } catch { throw new MidoriApiError('The server response could not be confirmed. Check the payment before retrying.', { code: 'NETWORK_UNCONFIRMED' }); }
    if (!response.ok) throw new MidoriApiError(result.error?.message || 'API request failed', { status: response.status, code: result.error?.code, details: result.error?.details });
    return result;
  }
}

export class EncryptedBlobSync {
  #revisions = new Map();

  constructor(client) { this.client = client; }

  revision(namespace) { return this.#revisions.get(namespace); }

  async read(namespace) {
    try {
      const blob = await this.client.request('GET', `/api/v1/blobs/${encodeURIComponent(namespace)}`);
      this.#revisions.set(namespace, blob.revision);
      return blob;
    } catch (error) {
      if (error.status !== 404) throw error;
      this.#revisions.set(namespace, '0');
      return null;
    }
  }

  async write(namespace, { ciphertext, cipherVersion = 1 }) {
    const expectedRevision = this.#revisions.get(namespace);
    if (expectedRevision === undefined) throw new Error('Read the current revision before writing encrypted data');
    const blob = await this.client.request('PUT', `/api/v1/blobs/${encodeURIComponent(namespace)}`, { expectedRevision, ciphertext, cipherVersion });
    this.#revisions.set(namespace, blob.revision);
    return blob;
  }

  async delete(namespace) {
    const expectedRevision = this.#revisions.get(namespace);
    if (!expectedRevision || expectedRevision === '0') throw new Error('Read the current blob before deleting it');
    const blob = await this.client.request('DELETE', `/api/v1/blobs/${encodeURIComponent(namespace)}`, { expectedRevision });
    this.#revisions.set(namespace, blob.revision);
    return blob;
  }
}
