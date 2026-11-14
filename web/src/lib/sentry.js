import * as Sentry from '@sentry/vue';
import { FRONT_DSN, privateOptions, sampleRate, isExpectedError, safeEvent } from '../../../packages/telemetry/index.js';
import { release } from './version.js';

function connectionType() {
  try { return window.localStorage?.getItem('_cs_onion') === 'true' ? 'tor' : 'web'; } catch { return 'web'; }
}

const enabled = () => import.meta.env.VITE_SENTRY_ENABLED !== 'false' && connectionType() !== 'tor';
const seen = new WeakSet();
const apiOrigin = import.meta.env.VITE_API_ORIGIN || window.location.origin;
const escape = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

Sentry.init({
  ...privateOptions(),
  beforeSendSpan: Sentry.withStaticSpan(privateOptions().beforeSendSpan),
  dsn: import.meta.env.VITE_SENTRY_DSN || FRONT_DSN,
  enabled: import.meta.env.VITE_SENTRY_ENABLED !== 'false',
  environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || import.meta.env.MODE,
  release,
  initialScope: { tags: { target: import.meta.env.VITE_BUILD_TYPE || 'web', platform: import.meta.env.VITE_PLATFORM || 'web', distribution: import.meta.env.VITE_DISTRIBUTION || 'web', connection: connectionType() } },
  tracesSampleRate: sampleRate(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE),
  tracePropagationTargets: [/^\/api\/v1\//, new RegExp(`^${escape(apiOrigin)}/api/v1/`)],
  integrations: integrations => integrations.filter(integration => ['EventFilters', 'FunctionToString', 'BrowserApiErrors', 'Breadcrumbs', 'GlobalHandlers', 'LinkedErrors', 'Dedupe', 'HttpContext'].includes(integration.name)).concat(
    Sentry.captureConsoleIntegration({ levels: ['error'] }),
  ),
  beforeSend(event, hint) {
    if (!enabled() || isExpectedError(hint?.originalException)) return null;
    return safeEvent(event);
  },
  beforeSendTransaction: event => enabled() ? safeEvent(event) : null,
});

export function setSentryConnection() {
  try { Sentry.setTag('connection', connectionType()); } catch { /* Diagnostics are optional. */ }
}

export function setSentryUser(authenticated = false) {
  try { Sentry.setUser(null); Sentry.setTag('authenticated', String(authenticated)); } catch { /* Diagnostics are optional. */ }
}

export function addSentryVueIntegration(app, router) {
  Sentry.addIntegration(Sentry.vueIntegration({ app, attachProps: false }));
  Sentry.addIntegration(Sentry.browserTracingIntegration({ router, traceFetch: connectionType() !== 'tor', traceXHR: connectionType() !== 'tor' }));
}

export function captureWalletError(error, operation = 'wallet.operation', tags = {}) {
  if (!enabled() || isExpectedError(error)) return;
  try {
    if (error && typeof error === 'object') { if (seen.has(error)) return; seen.add(error); }
    return Sentry.captureException(error, { tags: { ...tags, operation } });
  } catch { /* Diagnostics must not interrupt wallet operations. */ }
}
