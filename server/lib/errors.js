export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    Object.assign(this, { status, code, details });
  }
}

export function requireValue(condition, message = 'Invalid request') {
  if (!condition) throw new ApiError(400, 'INVALID_REQUEST', message);
}

export function exactKeys(value, required, optional = []) {
  requireValue(value && typeof value === 'object' && !Array.isArray(value));
  requireValue(required.every(key => Object.hasOwn(value, key)), 'Missing required field');
  requireValue(Object.keys(value).every(key => [...required, ...optional].includes(key)), 'Unknown field');
}

export const unauthorized = () => new ApiError(401, 'AUTHENTICATION_FAILED', 'Authentication failed or expired');
export const notFound = () => new ApiError(404, 'NOT_FOUND', 'Resource not found');
