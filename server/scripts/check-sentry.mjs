import { initializeSentry, captureServerError, flushSentry, Sentry } from '../lib/sentry.js';
import { FRONT_DSN, SERVER_DSN } from '../../packages/telemetry/index.js';

const target = process.argv[2];
if (!['server', 'front'].includes(target)) throw new Error('Usage: node scripts/check-sentry.mjs server|front');
initializeSentry('verification', { ...process.env, SENTRY_DSN: target === 'server' ? process.env.SENTRY_DSN || SERVER_DSN : process.env.VITE_SENTRY_DSN || FRONT_DSN, SENTRY_TRACES_SAMPLE_RATE: '0' });
if (!Sentry.getClient()) throw new Error('Sentry is disabled');
let status;
Sentry.getClient().on('afterSendEvent', (_event, response) => { status = response?.statusCode; });
const eventId = captureServerError(new Error('Midori Wallet Sentry integration verification'), { operation: 'sentry.verification' });
const flushed = await flushSentry(10000);
console.log(JSON.stringify({ target, eventId, status: status || 'no_response', accepted: flushed && status === 200 }));
await Sentry.close(2000);
if (!flushed || status !== 200) process.exitCode = 1;
