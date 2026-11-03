import { writeFile } from 'node:fs/promises';

const ref = name => ({ $ref: `#/components/schemas/${name}` });
const string = { type: 'string' };
const uuid = { type: 'string', format: 'uuid' };
const b64 = { type: 'string', pattern: '^[A-Za-z0-9_-]+$' };
const key = { ...b64, minLength: 43, maxLength: 43 };
const signature = { ...b64, minLength: 86, maxLength: 86 };
const address = { type: 'string', pattern: '^0x[0-9a-fA-F]{40}$' };
const atomic = { type: 'string', pattern: '^(0|[1-9][0-9]{0,77})$', description: 'Exact uint256 in base units; never a JSON number' };
const revision = { type: 'string', pattern: '^(0|[1-9][0-9]{0,18})$' };
const object = (properties, required = Object.keys(properties), additionalProperties = false) => ({ type: 'object', properties, required, additionalProperties });
const array = items => ({ type: 'array', items });
const json = schema => ({ content: { 'application/json': { schema } } });
const schemas = {
  Error: object({ error: object({ code: string, message: string, requestId: uuid, details: { type: 'object', additionalProperties: true } }, ['code', 'message', 'requestId']) }),
  Descriptor: object({ schema: { const: 'midori.safe-account' }, version: { const: 1 }, chainId: { const: 8453 }, accountType: { const: 'safe-erc4337' }, index: { type: 'integer', minimum: 0, maximum: 2147483647 }, derivationPath: string, ownerAddress: address, address, factoryAddress: address, factoryData: { type: 'string', pattern: '^0x[0-9a-fA-F]+$' }, threshold: { const: 1 }, safeModulesVersion: { const: '0.3.0' }, entryPoint: address, saltNonce: { type: 'string', pattern: '^0x[0-9a-f]{64}$' }, module: address, setup: address, sdk: object({ core: string, evm: string, erc4337: string, abstractionkit: string, ethers: string }) }),
  Registration: object({ walletPublicKey: key, devicePublicKey: key, descriptor: ref('Descriptor') }),
  RegistrationProof: object({ id: uuid, challenge: b64, walletSignature: signature, deviceSignature: signature, ownerSignature: { type: 'string', pattern: '^0x[0-9a-fA-F]{130}$' } }),
  Challenge: object({ id: uuid, purpose: string, challenge: b64, context: { type: 'object', additionalProperties: true }, expiresAt: { type: 'string', format: 'date-time' }, message: string, options: { type: 'object', additionalProperties: true } }, ['id', 'purpose', 'challenge', 'context', 'expiresAt', 'message']),
  Session: object({ walletId: uuid, deviceId: uuid, sessionId: uuid, token: key, expiresAt: { type: 'string', format: 'date-time' }, accountId: uuid }, ['walletId', 'deviceId', 'sessionId', 'token', 'expiresAt']),
  LoginOptions: object({ deviceId: uuid }),
  LoginProof: object({ id: uuid, challenge: b64, signature }),
  Recovery: object({ walletPublicKey: key, devicePublicKey: key }),
  RecoveryProof: object({ id: uuid, challenge: b64, walletSignature: signature, deviceSignature: signature }),
  WebAuthnProof: object({ id: uuid, challenge: b64, response: { type: 'object', additionalProperties: true, description: 'WebAuthn RegistrationResponseJSON or AuthenticationResponseJSON; user verification required' } }),
  Asset: object({ chainId: { const: 8453 }, contract: address, symbol: { type: 'string', enum: ['USDT', 'USDC'] }, decimals: { const: 6 }, representation: string }),
  AccountAsset: object({ contract: address, symbol: string, decimals: { const: 6 }, representation: string, address, balanceAtomic: { anyOf: [atomic, { type: 'null' }] }, observedBlock: { anyOf: [atomic, { type: 'null' }] } }),
  Account: object({ id: uuid, descriptor: ref('Descriptor'), chainId: { const: 8453 }, address, assets: array(ref('AccountAsset')) }),
  BlobWrite: object({ expectedRevision: revision, cipherVersion: { const: 1 }, ciphertext: { ...b64, minLength: 38, maxLength: 1398102 } }),
  BlobDelete: object({ expectedRevision: revision }),
  Blob: object({ namespace: string, revision, cipherVersion: { const: 1 }, ciphertext: { anyOf: [b64, { type: 'null' }] }, deleted: { type: 'boolean' }, updatedAt: { type: 'string', format: 'date-time' } }),
  DeviceEnrollment: object({ publicKey: key, signature }),
  Device: object({ id: uuid, publicKey: key, createdAt: { type: 'string', format: 'date-time' }, revokedAt: { type: ['string', 'null'], format: 'date-time' } }),
  Authenticator: object({ credentialId: b64, deviceId: uuid, deviceType: { enum: ['singleDevice', 'multiDevice'] }, backedUp: { type: 'boolean' }, createdAt: { type: 'string', format: 'date-time' }, revokedAt: { type: ['string', 'null'], format: 'date-time' } }),
  Configuration: object({ apiVersion: { const: 'v1' }, network: object({ name: { const: 'base' }, chainId: { const: 8453 }, testnet: { const: false } }), account: object({ schema: { const: 'midori.safe-account' }, version: { const: 1 }, type: { const: 'safe-erc4337' } }), capabilities: object({ registration: { type: 'boolean' }, devices: { type: 'boolean' }, webauthn: { type: 'boolean' }, encryptedBlobs: { type: 'boolean' }, payments: { type: 'boolean' }, sponsorship: { type: 'boolean' }, tags: { const: true }, history: { const: true }, signing: { const: 'client' }, optionalProviders: { const: false } }), gas: object({ midoriFeeAtomic: atomic, preferredMode: { const: 'token' }, tokenSymbol: { const: 'USDT' }, tokenContract: address, paymaster: { anyOf: [address, { type: 'null' }] }, maxFeeAtomic: atomic, enabled: { type: 'boolean' } }), limits: object({ maxDevices: { type: 'integer' }, maxAuthenticators: { type: 'integer' }, maxBlobBytes: { type: 'integer' } }), authentication: object({ rpID: string, origin: { type: 'string', format: 'uri' }, signedRequests: { const: 'ed25519-v1' }, sessionLifetimeSeconds: { type: 'integer' } }) }),
};
const spec = { openapi: '3.1.0', info: { title: 'Midori API', version: '1.0.0', description: 'PostgreSQL identity, devices and opaque encrypted storage on Base mainnet. Server authentication does not unlock funds or authorize chain transactions. Registration verifies the descriptor derivation and an owner message signature; it does not deploy a Safe.' }, servers: [{ url: '/' }], security: [], paths: {}, components: { securitySchemes: { Session: { type: 'http', scheme: 'bearer', description: 'Session token plus an Ed25519 signature over every request. Keys stay client-side.' }, Timestamp: { type: 'apiKey', in: 'header', name: 'X-Midori-Timestamp' }, Nonce: { type: 'apiKey', in: 'header', name: 'X-Midori-Nonce' }, Signature: { type: 'apiKey', in: 'header', name: 'X-Midori-Signature', description: 'Base64url Ed25519 signature over UTF-8: Midori request v1\\nMETHOD\\nexact path and query\\nUnix seconds\\nnonce\\nSHA256(token)\\nSHA256(raw body). A nonce can be used once per device. Clock tolerance 60 seconds.' } }, schemas } };
function endpoint(method, path, summary, request, response, { auth = false, status = 200 } = {}) {
  const parameters = [...path.matchAll(/\{([^}]+)\}/g)].map(([, name]) => ({ name, in: 'path', required: true, schema: name === 'namespace' ? { type: 'string', pattern: '^[a-z][a-z0-9_-]{0,63}$' } : path.includes('authenticators') ? b64 : uuid }));
  const operation = { operationId: summary.replace(/[^a-zA-Z0-9]/g, ''), summary, security: auth ? [{ Session: [], Timestamp: [], Nonce: [], Signature: [] }] : [], ...(parameters.length ? { parameters } : {}), responses: { [status]: { description: 'Success', ...json(response) }, ...Object.fromEntries([400, 401, 403, 404, 409, 429, 502, 503].map(code => [code, { description: { 400: 'Validation failed', 401: 'Expired, consumed or invalid authentication', 403: 'Origin denied', 404: 'Missing or foreign resource', 409: 'Duplicate, replay or revision conflict', 429: 'Rate limited', 502: 'Chain unavailable', 503: 'Database unavailable' }[code], ...json(ref('Error')) }])) } };
  if (request) operation.requestBody = { required: true, ...json(request) };
  spec.paths[path] ||= {}; spec.paths[path][method] = operation;
}
endpoint('get', '/healthz', 'Liveness', null, object({ status: { const: 'ok' }, service: { const: 'midori-api' }, apiVersion: { const: 'v1' } }));
endpoint('get', '/readyz', 'Readiness and migration checksum', null, object({ status: { enum: ['ready', 'unavailable'] }, database: { enum: ['ready', 'unavailable'] } }));
endpoint('get', '/api/v1/config', 'Public configuration', null, ref('Configuration'));
endpoint('get', '/api/v1/assets', 'Approved contracts', null, object({ assets: array(ref('Asset')) }));
for (const [base, input, proof, result] of [['registration', 'Registration', 'RegistrationProof', 'Session'], ['auth', 'LoginOptions', 'LoginProof', 'Session'], ['recovery', 'Recovery', 'RecoveryProof', 'Session'], ['auth/webauthn', 'LoginOptions', 'WebAuthnProof', 'Session']]) {
  endpoint('post', `/api/v1/${base}/options`, `${base} challenge`, ref(input), ref('Challenge'));
  endpoint('post', `/api/v1/${base}/verify`, `${base} verification`, ref(proof), ref(result), { status: ['registration', 'recovery'].includes(base) ? 201 : 200 });
}
const auth = { auth: true };
endpoint('get', '/api/v1/me', 'Own wallet and accounts', null, object({ walletId: uuid, deviceId: uuid, accounts: array(ref('Account')) }), auth);
endpoint('get', '/api/v1/accounts/{id}', 'Own chain account', null, ref('Account'), auth);
endpoint('get', '/api/v1/accounts/{id}/balances', 'Live Base balances at one block', null, object({ accountId: uuid, chainId: { const: 8453 }, address, blockNumber: atomic, readAt: { type: 'string', format: 'date-time' }, assets: array(object({ chainId: { const: 8453 }, contract: address, symbol: string, decimals: { const: 6 }, address, balanceAtomic: atomic })) }), auth);
endpoint('get', '/api/v1/devices', 'List devices', null, object({ devices: array(ref('Device')) }), auth);
endpoint('post', '/api/v1/devices', 'Enroll device with possession proof', ref('DeviceEnrollment'), object({ id: uuid, publicKey: key }), { ...auth, status: 201 });
endpoint('delete', '/api/v1/devices/{id}', 'Revoke device and its sessions and authenticators', null, object({ revoked: { const: true } }), auth);
endpoint('post', '/api/v1/auth/logout', 'Revoke current session', null, object({ revoked: { const: true } }), auth);
endpoint('get', '/api/v1/blobs/{namespace}', 'Read encrypted blob or deletion tombstone', null, ref('Blob'), auth);
endpoint('put', '/api/v1/blobs/{namespace}', 'Compare and swap encrypted blob', ref('BlobWrite'), ref('Blob'), auth);
endpoint('delete', '/api/v1/blobs/{namespace}', 'Delete ciphertext and preserve monotonic revision', ref('BlobDelete'), ref('Blob'), auth);
endpoint('post', '/api/v1/authenticators/options', 'Authenticator registration options', object({}), ref('Challenge'), auth);
endpoint('post', '/api/v1/authenticators/verify', 'Authenticator registration verification', ref('WebAuthnProof'), object({ registered: { const: true } }), { ...auth, status: 201 });
endpoint('get', '/api/v1/authenticators', 'List authenticators', null, object({ authenticators: array(ref('Authenticator')) }), auth);
endpoint('delete', '/api/v1/authenticators/{id}', 'Revoke authenticator', null, object({ revoked: { const: true } }), auth);
const alias = { type: 'string', pattern: '^[a-z0-9][a-z0-9-]{0,62}$' };
schemas.Tag = object({ alias, revision, chainId: { const: 8453 }, address });
schemas.TagResult = object({ tag: { anyOf: [ref('Tag'), { type: 'null' }] } });
schemas.TagOptions = object({ id: uuid, message: string, expiresAt: { type: 'string', format: 'date-time' }, alias, chainId: { const: 8453 }, address });
schemas.TagResolution = object({ id: uuid, alias, chainId: { const: 8453 }, address, revision, expiresAt: { type: 'string', format: 'date-time' } });
schemas.HistoryEntry = object({ id: { type: 'string', pattern: '^(e[0-9]+|p[0-9a-f-]{36})$' }, transactionHash: { type: ['string','null'] }, logIndex: { type: 'integer' }, chainId: { const: 8453 }, contract: address, symbol: { enum: ['USDT','USDC'] }, decimals: { const: 6 }, amountAtomic: atomic, from: address, to: address, incoming: { type: 'boolean' }, outgoing: { type: 'boolean' }, status: { enum: ['pending','included','confirmed','reverted','reorganized','expired'] }, confirmations: atomic, minConfirmations: { type: 'integer' }, timestamp: { type: 'string', format: 'date-time' }, origin: { enum: ['midori','unverified'] }, midoriFeeAtomic: { const: '0' }, intentId: { type: ['string','null'] }, feeMode: { type: ['string','null'] }, feeToken: { type: ['string','null'] }, actualFeeAtomic: { type: ['string','null'] }, maxFeeAtomic: { type: ['string','null'] }, userOperationHash: { type: ['string','null'] }, recipientTag: { type: ['string','null'] }, accountingState: { enum: ['pending','settled','review'] } });
schemas.History = object({ transactions: array(ref('HistoryEntry')), hasMore: { type: 'boolean' }, cursor: { type: ['string','null'] }, indexing: object({ enabled: { type: 'boolean' }, status: { enum: ['waiting','syncing','caught_up','stale'] }, lastIndexedBlock: { type: ['string','null'] }, updatedAt: { type: ['string','null'], format: 'date-time' } }) });
endpoint('get', '/api/v1/tag', 'Own registered Midori tag', null, ref('TagResult'), auth);
endpoint('post', '/api/v1/tag/options', 'Challenge to bind a tag to the owned account', object({ alias: string, accountId: uuid }), ref('TagOptions'), auth);
endpoint('post', '/api/v1/tag/verify', 'Register tag with account owner proof', object({ id: uuid, signature: { type: 'string', pattern: '^0x[0-9a-fA-F]{130}$' } }), ref('TagResult'), auth);
endpoint('post', '/api/v1/tags/resolve', 'Resolve tag to an expiring Base snapshot', object({ alias: string, chainId: { const: 8453 } }), ref('TagResolution'), auth);
endpoint('get', '/api/v1/accounts/{id}/history', 'Paginated own token history', null, ref('History'), auth);
spec.paths['/api/v1/accounts/{id}/history'].get.parameters.push(...[
  ['contract', address], ['cursor', { type: 'string', maxLength: 1000 }], ['limit', { type: 'integer', minimum: 1, maximum: 99 }]
].map(([name, schema]) => ({ name, in: 'query', schema })));
endpoint('get', '/api/v1/accounts/{id}/history/{eventId}', 'Own transaction receipt details', null, ref('HistoryEntry'), auth);
spec.paths['/api/v1/accounts/{id}/history/{eventId}'].get.parameters.find(parameter => parameter.name === 'eventId').schema = { type: 'string', pattern: '^(e?[0-9]+|p[0-9a-f-]{36})$' };
schemas.PaymentIntent = object({ id: uuid, accountId: uuid, chainId: { const: 8453 }, contract: address, amountAtomic: atomic, from: address, to: address, recipientKind: { enum: ['address','midori_tag'] }, recipientTag: { type: ['string','null'] }, feeMode: { enum: ['token','sponsored'] }, feeToken: { type: ['string','null'] }, status: { enum: ['preparing','quoted','authorized','submitted','ambiguous','included','confirmed','reverted','reorganized','expired','cancelled','rejected'] }, failureCode: { type: ['string','null'] }, expiresAt: { type: 'string', format: 'date-time' }, createdAt: { type: 'string', format: 'date-time' }, updatedAt: { type: 'string', format: 'date-time' }, quote: { type: ['object','null'], additionalProperties: true }, quoteDigest: { type: ['string','null'] }, userOperationHash: { type: ['string','null'] }, transactionHash: { type: ['string','null'] }, actualFeeAtomic: { type: ['string','null'] }, accountingState: { enum: ['pending','settled','review'] } });
schemas.PaymentRequest = object({ idempotencyKey: uuid, contract: address, amountAtomic: { anyOf: [atomic, { const: 'max' }] }, recipient: { oneOf: [object({ kind: { const: 'address' }, address }), object({ kind: { const: 'midori_tag' }, resolutionId: uuid })] }, feeMode: { enum: ['token','sponsored'] }, feeToken: { anyOf: [address, { type: 'null' }] }, maxFeeAtomic: atomic });
endpoint('post', '/api/v1/accounts/{id}/intents', 'Review an idempotent payment and its network cost', ref('PaymentRequest'), ref('PaymentIntent'), auth);
endpoint('get', '/api/v1/accounts/{id}/intents', 'Restore payment tracking on any device', null, object({ intents: array(ref('PaymentIntent')) }), auth);
endpoint('get', '/api/v1/intents/{id}', 'Track a persisted payment', null, ref('PaymentIntent'), auth);
endpoint('post', '/api/v1/intents/{id}/authorize', 'Authorize the exact quote with the local owner signature', object({ quoteDigest: { type: 'string', pattern: '^[0-9a-f]{64}$' }, signature: { type: 'string', pattern: '^0x[0-9a-fA-F]{130}$' } }), ref('PaymentIntent'), auth);
endpoint('post', '/api/v1/intents/{id}/cancel', 'Cancel an unsigned payment quote', object({}), ref('PaymentIntent'), auth);
await writeFile(new URL('../openapi.json', import.meta.url), JSON.stringify(spec, null, 2) + '\n');
