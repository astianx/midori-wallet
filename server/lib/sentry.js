import * as Sentry from '@sentry/node';
import fs from 'node:fs';
import { SERVER_DSN, privateOptions, sampleRate, isExpectedError, safeUrl } from '../../packages/telemetry/index.js';

let initialized = false;
export function initializeSentry(service = 'server', env = process.env) {
  if (initialized || env.SENTRY_ENABLED === 'false' || env.NODE_ENV === 'test') return;
  const version = JSON.parse(fs.readFileSync(new URL('../package.json', import.meta.url), 'utf8')).version;
  Sentry.init({
    ...privateOptions(),
    beforeSendSpan: Sentry.withStaticSpan(privateOptions().beforeSendSpan),
    dsn: env.SENTRY_DSN || SERVER_DSN,
    environment: env.SENTRY_ENVIRONMENT || env.NODE_ENV || 'development',
    release: env.SENTRY_RELEASE || `midori-wallet.server@${version}+${env.VITE_COMMIT || env.GITHUB_SHA || 'local'}`,
    initialScope: { tags: { service } },
    tracesSampleRate: sampleRate(env.SENTRY_TRACES_SAMPLE_RATE),
    integrations: integrations => integrations.filter(integration => ['EventFilters', 'FunctionToString', 'LinkedErrors', 'Dedupe', 'Http', 'NodeFetch', 'OnUncaughtException', 'OnUnhandledRejection', 'NodeContext'].includes(integration.name)).concat(Sentry.expressIntegration({ shouldHandleError: false })),
    tracePropagationTargets: [],
    maxValueLength: 1000,
  });
  initialized = true;
}

export function captureServerError(error, { operation = 'unknown', code, requestId, route, status } = {}) {
  if (isExpectedError(error) && !(status >= 500)) return;
  try {
    if (!Sentry.getClient()) return;
    return Sentry.captureException(error, { tags: { operation, ...(code ? { code } : {}), ...(requestId ? { request_id: requestId } : {}), ...(route ? { route: safeUrl(route) } : {}), ...(status ? { status: String(status) } : {}) } });
  } catch { /* Diagnostics must not interrupt wallet operations. */ }
}

export async function flushSentry(timeout = 2000) {
  try { return await Sentry.flush(timeout); } catch { return false; }
}

export { Sentry };
