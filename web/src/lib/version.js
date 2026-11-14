import { clientRelease } from '../../../packages/telemetry/index.js';

export const release = clientRelease(import.meta.env);

const COMMIT = (import.meta.env.VITE_COMMIT || 'local').substring(0, 7);
export const prettyVersion = `v${import.meta.env.VITE_VERSION}@${import.meta.env.VITE_DISTRIBUTION} (${COMMIT})`;
