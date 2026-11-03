import { createHash, createPublicKey, randomBytes, verify } from 'node:crypto';
import { requireValue } from './errors.js';

export const sha256 = value => createHash('sha256').update(value).digest('hex');
export const randomToken = () => randomBytes(32).toString('base64url');
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function decodeBase64(value, min, max = min) {
  requireValue(typeof value === 'string' && /^[A-Za-z0-9_-]+$/.test(value), 'Expected base64url');
  const buffer = Buffer.from(value, 'base64url');
  requireValue(buffer.length >= min && buffer.length <= max && buffer.toString('base64url') === value, 'Invalid base64url length or encoding');
  return buffer;
}

export function publicKey(value) { decodeBase64(value, 32); return value; }

export function verifyEd25519(key, message, signature) {
  try {
    const bytes = decodeBase64(key, 32);
    const der = Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), bytes]);
    return verify(null, Buffer.from(message), createPublicKey({ key: der, type: 'spki', format: 'der' }), decodeBase64(signature, 64));
  } catch { return false; }
}

export function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
  return JSON.stringify(value);
}

export function challengeMessage(origin, id, purpose, challenge, context) {
  return `Midori API v1\norigin:${origin}\npurpose:${purpose}\nid:${id}\nchallenge:${challenge}\ncontext:${sha256(canonical(context))}`;
}

export function requestMessage({ method, target, timestamp, nonce, token, body = '' }) {
  return `Midori request v1\n${method.toUpperCase()}\n${target}\n${timestamp}\n${nonce}\n${sha256(token)}\n${sha256(body)}`;
}
