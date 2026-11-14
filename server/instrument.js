import { initializeSentry } from './lib/sentry.js';
initializeSentry(process.argv[1]?.endsWith('worker.js') ? 'worker' : 'server');
