import * as Sentry from '@sentry/electron/main';
import { app } from 'electron';
import { FRONT_DSN, privateOptions, sampleRate } from '../dist/telemetry.js';
import { VITE_SENTRY_DSN, VITE_SENTRY_ENVIRONMENT, VITE_SENTRY_ENABLED, VITE_SENTRY_TRACES_SAMPLE_RATE, release } from './constants.js';

if (VITE_SENTRY_ENABLED !== 'false') {
  Sentry.init({
    ...privateOptions(),
    dsn: VITE_SENTRY_DSN || FRONT_DSN,
    environment: VITE_SENTRY_ENVIRONMENT || (app.isPackaged ? 'production' : 'development'),
    release,
    transport: Sentry.makeElectronTransport,
    tracesSampleRate: sampleRate(VITE_SENTRY_TRACES_SAMPLE_RATE),
    beforeSendSpan: Sentry.withStaticSpan(privateOptions().beforeSendSpan),
    initialScope: { tags: { service: 'electron.main', platform: process.platform } },
    integrations: () => [
      Sentry.onUncaughtExceptionIntegration(),
      Sentry.onUnhandledRejectionIntegration(),
      Sentry.linkedErrorsIntegration(),
      Sentry.normalizePathsIntegration(),
      Sentry.electronContextIntegration(),
    ],
  });
  app.on('render-process-gone', (_event, _contents, details) => {
    if (details.reason === 'clean-exit') return;
    Sentry.captureException(new Error(`Renderer exited: ${details.reason}`), { tags: { operation: 'electron.renderer', reason: details.reason } });
  });
  app.on('child-process-gone', (_event, details) => {
    if (details.reason === 'clean-exit') return;
    Sentry.captureException(new Error(`Child process exited: ${details.reason}`), { tags: { operation: 'electron.child', reason: details.reason } });
  });
}
