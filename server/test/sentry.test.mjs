import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { FRONT_DSN, SERVER_DSN, sentryOrigin, safeEvent, safeSpan, scrubText, privateOptions, sampleRate, isExpectedError, clientRelease } from '../../packages/telemetry/index.js';
import { createApp } from '../app.js';
import { ApiError } from '../lib/errors.js';
import { Sentry } from '../lib/sentry.js';
import { MidoriClient } from '../../web/src/lib/api/MidoriClient.js';

const phrase = 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
const privateKey = '0x' + 'a'.repeat(64);
const address = '0x' + 'b'.repeat(40);
const token = 'session_token_' + 'q'.repeat(50);

function assertPrivate(value) {
  const serialized = JSON.stringify(value);
  for (const secret of [phrase, privateKey, address, token, 'hunter2', 'alice@example.com', '654321']) assert.ok(!serialized.includes(secret), `Leaked ${secret}`);
}

test('Sentry policy removes wallet material, identity, HTTP payloads, arbitrary extras, DOM and console data while preserving diagnostic stacks', () => {
  const event = safeEvent({
    event_id: '1'.repeat(32), release: 'wallet@1.0.5', level: 'error',
    message: `Failure ${phrase} privateKey=${privateKey} pin=654321 password=hunter2 alice@example.com`,
    user: { id: 'wallet-id', email: 'alice@example.com', ip_address: '127.0.0.1' },
    request: { url: `https://wallet.test/api/v1/accounts/${address}?token=${token}`, headers: { Authorization: token }, data: { mnemonic: phrase } },
    extra: { signature: privateKey, account: { walletSeed: phrase } },
    contexts: { trace: { trace_id: '2'.repeat(32), span_id: '3'.repeat(16), data: { token } }, vue: { propsData: { phrase } } },
    exception: { values: [{ type: 'Error', value: `${privateKey} ${phrase}`, stacktrace: { frames: [{ filename: 'https://wallet.test/assets/app.js?token=' + token, function: 'sendPayment', lineno: 42, vars: { privateKey }, context_line: phrase }] } }] },
    tags: { operation: 'payment.submit', request_id: 'f2123ee0-9f55-4929-8096-fb1f08123dc7', walletId: 'wallet-id' },
    breadcrumbs: [{ category: 'console', message: phrase }, { category: 'ui.click', message: '654321' }, { category: 'fetch', data: { url: `https://wallet.test/api/v1/accounts/${address}?token=${token}`, method: 'POST', status_code: 503, body: phrase } }],
  });
  assertPrivate(event);
  assert.equal(event.exception.values[0].stacktrace.frames[0].function, 'sendPayment');
  assert.equal(event.exception.values[0].stacktrace.frames[0].lineno, 42);
  assert.equal(event.exception.values[0].stacktrace.frames[0].filename, 'https://wallet.test/assets/app.js');
  assert.equal(event.tags.operation, 'payment.submit');
  assert.equal(event.tags.request_id, 'f2123ee0-9f55-4929-8096-fb1f08123dc7');
  assert.equal(event.breadcrumbs.length, 1);
  assert.equal(event.breadcrumbs[0].data.url, 'https://wallet.test/api/v1/accounts/:id');
  for (const field of ['user', 'request', 'extra']) assert.equal(event[field], undefined);
});

test('trace privacy removes query parameters, SQL, headers and values from spans and transactions', () => {
  const span = safeSpan({ trace_id: '1'.repeat(32), span_id: '2'.repeat(16), start_timestamp: 1, timestamp: 2, op: 'db', description: `SELECT '${privateKey}'`, data: { 'db.query': phrase, 'http.request.header.authorization': token, 'http.response.status_code': 503 } });
  assert.equal(span.description, 'database.query');
  assert.equal(span.data['http.response.status_code'], 503);
  const transaction = safeEvent({ type: 'transaction', transaction: `GET /api/v1/accounts/${address}/history?token=${token}`, spans: [span] });
  assertPrivate(transaction);
  assert.equal(transaction.transaction, 'GET /api/v1/accounts/:id/history');
  assert.equal(privateOptions().traceLifecycle, 'static');
  assert.equal(privateOptions().beforeSendLog({ message: phrase }), null);
  assert.equal(privateOptions().beforeSendMetric({ attributes: { token } }), null);
});

test('configuration validates public DSNs, bounds trace volume and preserves actionable infrastructure errors', () => {
  assert.equal(sentryOrigin(FRONT_DSN), 'https://o508466.ingest.us.sentry.io');
  assert.equal(sentryOrigin(SERVER_DSN), sentryOrigin(FRONT_DSN));
  for (const dsn of ['http://key@example.com/12', 'https://example.com/12', 'https://key@example.com/project', 'https://key:secret@example.com/12']) assert.throws(() => sentryOrigin(dsn));
  assert.equal(sampleRate('0'), 0); assert.equal(sampleRate('1'), 1);
  for (const rate of ['garbage', '-1', '2', 'NaN']) assert.equal(sampleRate(rate), 0.1);
  for (const error of [{ name: 'NotAllowedError' }, { status: 401 }, { code: 4001 }]) assert.equal(isExpectedError(error), true);
  assert.equal(isExpectedError({ status: 503 }), false);
  assert.equal(clientRelease({ VITE_VERSION: '1.0.5', VITE_COMMIT: 'commit' }), 'midori-wallet.web.web.web@1.0.5+commit');
  assertPrivate({ message: scrubText(`https://user:hunter2@rpc.test/path?key=${token}#${phrase}`) });
});

test('the real SDK transport receives only sanitized events and traces', async () => {
  const envelopes = [];
  const previous = Sentry.getClient();
  Sentry.init({
    ...privateOptions(), dsn: SERVER_DSN, defaultIntegrations: false, skipOpenTelemetrySetup: true,
    beforeSendSpan: Sentry.withStaticSpan(safeSpan), tracesSampleRate: 1,
    transport: () => ({ send: async envelope => { envelopes.push(envelope); return { statusCode: 200 }; }, flush: async () => true }),
  });
  try {
    Sentry.captureException(new Error(`Payment failed ${privateKey}`), { extra: { phrase, token }, user: { email: 'alice@example.com' } });
    await Sentry.startSpan({ name: 'worker.task', op: 'worker.task' }, async () => {});
    assert.equal(await Sentry.flush(3000), true);
    assert.ok(envelopes.some(([, items]) => items.some(([headers]) => headers.type === 'event')));
    assert.ok(envelopes.some(([, items]) => items.some(([headers]) => headers.type === 'transaction')));
    for (const envelope of envelopes) assertPrivate(envelope);
  } finally { await Sentry.close(1000); Sentry.getCurrentScope().setClient(previous); }
});

async function withApi(reportError, action) {
  const config = { trustProxy: false, allowedOrigins: [], publicOrigin: 'http://localhost:5173' };
  const db = { query: async () => { throw new Error('Database failed'); } };
  const app = createApp({ db, config, reportError });
  const server = createServer(app);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await action(`http://127.0.0.1:${server.address().port}`); }
  finally { await new Promise(resolve => server.close(resolve)); }
}

test('API reports 5xx once with a request ID, excludes expected 4xx, and reporting failures preserve API responses', async () => {
  const reports = [];
  await withApi((...args) => reports.push(args), async origin => {
    const failed = await fetch(origin + '/api/v1/assets');
    assert.equal(failed.status, 500);
    const body = await failed.json();
    assert.equal(reports.length, 1);
    assert.equal(reports[0][1].requestId, body.error.requestId);
    assert.equal(reports[0][1].route, '/api/v1/assets');
    assert.equal((await fetch(origin + '/api/v1/unknown')).status, 404);
    assert.equal((await fetch(origin + '/api/v1/registration/options', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{' })).status, 400);
    assert.equal(reports.length, 1);
  });
  await withApi(() => { throw new Error('Sentry unavailable'); }, async origin => assert.equal((await fetch(origin + '/api/v1/assets')).status, 500));
});

test('database constraint conflicts do not become false Sentry server errors', async () => {
  const reports = [];
  const app = createApp({ db: { query: async () => { throw Object.assign(new Error('Duplicate'), { code: '23505' }); } }, config: { allowedOrigins: [] }, reportError: (...args) => reports.push(args) });
  const server = createServer(app);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { assert.equal((await fetch(`http://127.0.0.1:${server.address().port}/api/v1/assets`)).status, 409); assert.equal(reports.length, 0); }
  finally { await new Promise(resolve => server.close(resolve)); }
});

test('client reports swallowed API failures with correlation IDs and never replays payment submissions', async () => {
  const reports = []; let calls = 0;
  const client = new MidoriClient({ origin: 'https://wallet.test', reportError: (...args) => reports.push(args), fetchImpl: async () => { calls++; return { ok: false, status: 503, json: async () => ({ error: { code: 'DATABASE_UNAVAILABLE', message: 'Temporarily unavailable', requestId: 'f2123ee0-9f55-4929-8096-fb1f08123dc7' } }) }; } });
  await assert.rejects(client.request('POST', '/api/v1/intents/identifier/authorize', { signature: privateKey }), { status: 503 });
  assert.equal(calls, 1); assert.equal(reports.length, 1);
  assert.equal(reports[0][2].request_id, 'f2123ee0-9f55-4929-8096-fb1f08123dc7');
  assertPrivate(safeEvent({ tags: reports[0][2] }));
  client.reportError = () => { throw new Error('Reporting failed'); };
  client.fetchImpl = async () => { calls++; throw new Error('Offline'); };
  await assert.rejects(client.request('POST', '/api/v1/intents/identifier/authorize'), { code: 'NETWORK_UNCONFIRMED' });
  assert.equal(calls, 2);
  client.fetchImpl = async () => ({ ok: false, status: 401, json: async () => ({ error: { code: 'AUTHENTICATION_FAILED' } }) });
  client.reportError = (...args) => reports.push(args);
  await assert.rejects(client.request('GET', '/api/v1/me'), { status: 401 });
  assert.equal(reports.length, 1);
  assert.equal(isExpectedError(new ApiError(409, 'RETRY_REQUIRED', 'Retry')), true);
});

test('client reports malformed responses without exposing request payloads', async () => {
  const reports = [];
  const client = new MidoriClient({ origin: 'https://wallet.test', reportError: (...args) => reports.push(args), fetchImpl: async () => ({ ok: true, status: 200, json: async () => { throw new Error(phrase); } }) });
  await assert.rejects(client.request('POST', '/api/v1/intents/example/authorize', { signature: privateKey }), { code: 'NETWORK_UNCONFIRMED' });
  assert.equal(reports.length, 1);
  assert.equal(reports[0][1], 'api.response');
  assertPrivate({ message: reports[0][0].message, tags: reports[0][2] });
});

test('API CORS permits distributed tracing headers and expected failures still use the original status', async () => {
  const server = createServer(createApp({ db: {}, config: { allowedOrigins: ['https://wallet.test'] } }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/v1/me`, { method: 'OPTIONS', headers: { origin: 'https://wallet.test', 'access-control-request-headers': 'sentry-trace,baggage' } });
    assert.equal(response.status, 204);
    assert.ok(response.headers.get('access-control-allow-headers').includes('sentry-trace,baggage'));
  } finally { await new Promise(resolve => server.close(resolve)); }
});
