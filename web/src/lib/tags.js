export function normalizeTagAlias(value) {
  return typeof value === 'string' ? value.trim().replace(/^[$@]/, '').toLowerCase() : '';
}

export function isValidTagAlias(value) {
  return typeof value === 'string' && /^[$@]?[a-z0-9][a-z0-9-]{0,62}$/i.test(value.trim());
}

export function isTagRecipient(value) {
  return typeof value === 'string' && /^[$@]/.test(value.trim()) && isValidTagAlias(value);
}

export function formatMidoriTag(value) {
  const alias = normalizeTagAlias(value);
  return alias ? `$${alias}` : '';
}
