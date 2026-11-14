export const SERVER_DSN = 'https://84d45c7563a8ec32f4038058fcb0c0b7@o508466.ingest.us.sentry.io/4510206980325376';
export const FRONT_DSN = 'https://bd284dfbbb53114b30579b7fc06013c2@o508466.ingest.us.sentry.io/4510206986158080';

const routeParts = new Set('api v1 healthz readyz config openapi.json updates registration options verify auth recovery webauthn assets accounts balances history intents authorize cancel ramp orders tag tags resolve devices logout blobs authenticators me webhooks transak'.split(' '));
const safeTags = new Set(['service', 'target', 'platform', 'distribution', 'connection', 'authenticated', 'operation', 'stage', 'code', 'status', 'request_id', 'route', 'reason']);
const pick = (value, keys) => Object.fromEntries(keys.filter(key => value?.[key] !== undefined).map(key => [key, value[key]]));

export function sampleRate(value, fallback = 0.1) {
  if (value === undefined || value === '') return fallback;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 && number <= 1 ? number : fallback;
}

export function clientRelease(env) {
  return env.VITE_SENTRY_RELEASE || `midori-wallet.${env.VITE_BUILD_TYPE || 'web'}.${env.VITE_PLATFORM || 'web'}.${env.VITE_DISTRIBUTION || 'web'}@${env.VITE_VERSION || '1.0.5'}+${env.VITE_COMMIT || 'local'}`;
}

export function sentryOrigin(dsn) {
  if (!dsn) return '';
  const url = new URL(dsn);
  if (url.protocol !== 'https:' || !url.username || !/^\/\d+$/.test(url.pathname) || url.password || url.search || url.hash) throw new Error('Invalid Sentry DSN; use the complete HTTPS Client Key DSN');
  return url.origin;
}

export function safeUrl(value) {
  if (typeof value !== 'string') return undefined;
  try {
    const relative = value.startsWith('/');
    const url = new URL(value, 'https://redacted.invalid');
    if (!['http:', 'https:', 'app:', 'file:'].includes(url.protocol)) return '[Filtered URL]';
    let pathname = url.pathname;
    if (pathname.startsWith('/api/')) pathname = pathname.split('/').map(part => !part || routeParts.has(part) || /^:[a-z]+$/i.test(part) ? part : ':id').join('/');
    else if (!/\.(?:m?js|vue|css|html|map)$/.test(pathname)) pathname = pathname === '/' ? '/' : '/:path';
    if (url.protocol === 'file:') pathname = pathname.replace(/^.*\/(?:resources|app\.asar)\//, '/');
    return `${relative ? '' : url.origin === 'null' ? url.protocol + '//' : url.origin}${pathname}`;
  } catch { return '[Filtered URL]'; }
}

export function scrubText(value) {
  if (typeof value !== 'string') return undefined;
  return value
    .replace(/(?:https?|app|file):\/\/[^\s<>"')]+/gi, match => safeUrl(match))
    .replace(/\/api\/[^\s?]+(?:\?[^\s]*)?/g, match => safeUrl(match))
    .replace(/\{[\s\S]*\}|\[[\s\S]*\]/g, '[Filtered data]')
    .replace(/\b(?:[a-z]+\s+){11,}[a-z]+\b/gi, '[Filtered phrase]')
    .replace(/\b(?:Bearer\s+)?eyJ[A-Za-z0-9_.-]+/g, '[Filtered token]')
    .replace(/\b(?:0x)?[a-f0-9]{32,}\b/gi, '[Filtered hex]')
    .replace(/\b[A-Za-z0-9_+/-]{32,}={0,2}\b/g, '[Filtered token]')
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, '[Filtered email]')
    .replace(/\b\d{4,6}\b/g, '[Filtered number]')
    .replace(/\b(?:password|pin|mnemonic|seed|secret|token|authorization|signature|privateKey|walletSeed|deviceSeed)\s*[:=]\s*[^\s,;]+/gi, '[Filtered credential]')
    .slice(0, 1000);
}

function safeFrame(frame) {
  return {
    ...pick(frame, ['lineno', 'colno', 'in_app']),
    ...Object.fromEntries(['function', 'module'].filter(key => frame[key]).map(key => [key, scrubText(frame[key])])),
    ...(frame.filename ? { filename: frame.filename.includes('://') ? safeUrl(frame.filename) : scrubText(frame.filename.replace(/\/home\/[^/]+\//, '/home/[Filtered]/')) } : {}),
  };
}

export function safeBreadcrumb(crumb) {
  if (['fetch', 'xhr', 'http'].includes(crumb.category)) {
    return { ...pick(crumb, ['timestamp', 'category', 'type', 'level']), data: { ...pick(crumb.data, ['method', 'status_code']), url: safeUrl(crumb.data?.url) } };
  }
  if (crumb.category === 'navigation') return { ...pick(crumb, ['timestamp', 'category', 'type']), data: { from: safeUrl(crumb.data?.from), to: safeUrl(crumb.data?.to) } };
  return null;
}

export function safeSpan(span) {
  const result = pick(span, ['span_id', 'trace_id', 'parent_span_id', 'start_timestamp', 'timestamp', 'status', 'op', 'origin']);
  const description = span.description || '';
  result.description = span.op?.startsWith('db') ? 'database.query' : scrubText(description);
  result.data = pick(span.data, ['http.request.method', 'http.response.status_code', 'network.protocol.version', 'sentry.op', 'sentry.origin']);
  if (span.data?.['url.full']) result.data['url.full'] = safeUrl(span.data['url.full']);
  return result;
}

export function safeEvent(event) {
  try {
    const result = pick(event, ['event_id', 'timestamp', 'start_timestamp', 'platform', 'level', 'type', 'release', 'environment', 'sdk', 'debug_meta']);
    if (event.message) result.message = scrubText(event.message);
    if (event.logentry) result.logentry = { formatted: scrubText(event.logentry.formatted || event.logentry.message) };
    if (event.transaction) result.transaction = scrubText(event.transaction.replace(/\/api\/[^\s?]+(?:\?[^\s]*)?/g, value => safeUrl(value)));
    if (event.transaction_info) result.transaction_info = pick(event.transaction_info, ['source']);
    result.tags = Object.fromEntries(Object.entries(event.tags || {}).filter(([key]) => safeTags.has(key)).map(([key, value]) => [key, key === 'request_id' && /^[a-f0-9-]{36}$/i.test(value) ? value : key === 'route' ? safeUrl(String(value)) : scrubText(String(value))]));
    result.contexts = {};
    for (const [name, keys] of Object.entries({ trace: ['trace_id', 'span_id', 'parent_span_id', 'op', 'status', 'origin'], browser: ['name', 'version'], os: ['name', 'version'], runtime: ['name', 'version'], app: ['app_name', 'app_version'], device: ['arch', 'family'] })) {
      if (event.contexts?.[name]) result.contexts[name] = pick(event.contexts[name], keys);
    }
    if (event.contexts?.vue) result.contexts.vue = Object.fromEntries(['componentName', 'lifecycleHook'].filter(key => event.contexts.vue[key]).map(key => [key, scrubText(event.contexts.vue[key])]));
    if (event.exception?.values) result.exception = { values: event.exception.values.map(error => ({
      type: scrubText(error.type), value: scrubText(error.value),
      ...(error.mechanism ? { mechanism: pick(error.mechanism, ['type', 'handled']) } : {}),
      ...(error.stacktrace?.frames ? { stacktrace: { frames: error.stacktrace.frames.map(safeFrame) } } : {}),
    })) };
    if (event.breadcrumbs) result.breadcrumbs = event.breadcrumbs.map(safeBreadcrumb).filter(Boolean);
    if (event.spans) result.spans = event.spans.map(safeSpan);
    if (event.measurements) result.measurements = Object.fromEntries(Object.entries(event.measurements).filter(([key, value]) => ['lcp', 'fcp', 'inp', 'cls', 'ttfb'].includes(key) && Number.isFinite(value?.value)).map(([key, value]) => [key, pick(value, ['value', 'unit'])]));
    return result;
  } catch { return null; }
}

export function isExpectedError(error) {
  if (!error) return false;
  if (['AbortError', 'NotAllowedError'].includes(error.name)) return true;
  const status = Number(error.status || error.statusCode);
  return (status >= 400 && status < 500) || error.code === 'ACTION_REJECTED' || error.code === 4001 || error.message === 'fingerprint_error';
}

export function privateOptions() {
  return {
    sendDefaultPii: false,
    sendClientReports: false,
    autoSessionTracking: false,
    beforeSendLog: () => null,
    beforeSendMetric: () => null,
    traceLifecycle: 'static',
    maxBreadcrumbs: 30,
    attachStacktrace: true,
    beforeSend: safeEvent,
    beforeSendTransaction: safeEvent,
    beforeSendSpan: safeSpan,
    beforeBreadcrumb: safeBreadcrumb,
    dataCollection: { userInfo: false, httpBodies: [], httpHeaders: false, cookies: false, urlQueryParams: false, databaseQueryData: false, stackFrameVariables: false, frameContextLines: 0 },
  };
}
