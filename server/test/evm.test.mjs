import test from 'node:test';
import assert from 'node:assert/strict';
import { hexlify, toUtf8Bytes, toUtf8String, ZeroAddress } from 'ethers';
import { SafeAccountV0_3_0 } from 'abstractionkit';
import { STABLECOIN_NETWORKS, evmNetworks, stablecoinNetwork } from '../../config/stablecoin-networks.js';
import { createWalletSession } from '../../packages/wallet-core/src/index.js';
import { evmAccount, validateEvmPayment, signEvmTransfer, inspectEvmTransfer, evmTransactionHash, evmToken } from '../../packages/wallet-core/src/evm.js';
import { paymentOperation } from '../../packages/wallet-core/src/payment.js';
import { evmGateway } from '../lib/evm.js';
import { evmPaymentService } from '../lib/evm-payments.js';
import { userOperationEvents } from '../lib/payment-provider.js';
import AssetCatalog from '../../web/src/lib/account/AssetCatalog.js';
const seed = new Uint8Array(64).fill(1);
const session = await createWalletSession(seed, { chainId: 8453 });
const descriptor = session.descriptor;
session.dispose();
const network = stablecoinNetwork('ethereum'), asset = network.assets.find(asset => asset.symbol === 'USDT');
const target = '0x' + '12'.repeat(20);
const operation = (chain = network, token = asset) => ({ sender: descriptor.address, nonce: '0x7', factory: descriptor.factoryAddress, factoryData: descriptor.factoryData, callData: SafeAccountV0_3_0.createAccountCallDataSingleTransaction({ to: token.contract, value: 0n, data: evmToken.encodeFunctionData('transfer', [target, 12345678901234567890n]) }), callGasLimit: '0x13880', verificationGasLimit: '0x493e0', preVerificationGas: '0xc350', maxFeePerGas: '0x3b9aca00', maxPriorityFeePerGas: '0x3b9aca00', paymaster: null, paymasterData: null, signature: '0x' });
const quote = (chain = network, token = asset) => ({ network: chain.id, chainId: chain.chainId, from: descriptor.address, to: target, contract: token.contract, symbol: token.symbol, decimals: token.decimals, amountAtomic: '12345678901234567890', nonce: '7', operation: operation(chain, token), maxFeeAtomic: '430000000000000', nativeSymbol: chain.nativeSymbol, expiresAt: new Date(Date.now() + 120000).toISOString() });
function provider(overrides = {}) {
  const calls = [];
  const fetchImpl = async (url, options) => {
    const { method, params } = JSON.parse(options.body); calls.push({ url, method, params });
    const values = { eth_chainId: '0x1', eth_supportedEntryPoints: [descriptor.entryPoint], eth_blockNumber: '0x100', eth_call: params => params[0].data.startsWith('0x35567e1a') ? '0x7' : params[0].to.toLowerCase() === descriptor.entryPoint.toLowerCase() ? '0x0' : '0xffffffffffffffffffffffffffff', eth_getBalance: '0xde0b6b3a7640000', eth_gasPrice: '0x3b9aca00', eth_getCode: '0x', eth_estimateUserOperationGas: { callGasLimit: '0x13880', verificationGasLimit: '0x493e0', preVerificationGas: '0xc350' }, eth_getUserOperationReceipt: null, eth_getUserOperationByHash: null, eth_getTransactionReceipt: null, eth_getLogs: [], ...overrides };
    if (!Object.hasOwn(values, method)) throw Error('Unexpected RPC ' + method);
    const result = typeof values[method] === 'function' ? values[method](params, url) : values[method];
    return { ok: true, json: async () => ({ result }) };
  };
  return { calls, gateway: evmGateway({ fetchImpl }) };
}
test('catalog contains only operational networks and never offers Base USDT', () => {
  assert.deepEqual(STABLECOIN_NETWORKS.map(item => item.id).sort(), ['arbitrum', 'avalanche', 'base', 'ethereum', 'polygon']);
  assert.ok(STABLECOIN_NETWORKS.every(item => item.enabled && item.accountType === 'safe'));
  assert.deepEqual(stablecoinNetwork('base').assets.map(item => item.symbol), ['USDC']);
  assert.deepEqual(evmNetworks.map(item => item.chainId).sort((a, b) => a - b), [1, 137, 42161, 43114]);
  const catalog = new AssetCatalog(), bridge = catalog.all.find(item => item.platform === 'base' && item.symbol === 'USDT');
  assert.equal(bridge.addable, false); assert.equal(bridge.supported, true);
});
test('every token on every network signs for the original Base Safe, with replay protection', async () => {
  const hashes = new Set();
  for (const chain of evmNetworks) for (const token of chain.assets) {
    assert.equal(evmAccount(descriptor).accountAddress.toLowerCase(), descriptor.address.toLowerCase());
    const raw = await signEvmTransfer(seed, descriptor, quote(chain, token), { ...token, chainId: chain.chainId });
    const parsed = inspectEvmTransfer(raw, chain, descriptor);
    assert.equal(parsed.operation.sender, descriptor.address);
    assert.equal(parsed.amountAtomic, '12345678901234567890');
    assert.equal(parsed.hash, evmTransactionHash(raw));
    assert.ok(!hashes.has(parsed.hash)); hashes.add(parsed.hash);
  }
  assert.equal(hashes.size, 8);
  assert.notEqual(descriptor.address, descriptor.ownerAddress);
});
test('local approval rejects changed account, chain, token, recipient, amount, deployment and maximum fee', async () => {
  const value = quote();
  for (const change of [{ chainId: 137 }, { network: 'base' }, { contract: target }, { from: descriptor.ownerAddress }, { to: descriptor.address }, { to: asset.contract }, { to: ZeroAddress }, { amountAtomic: '-1' }, { amountAtomic: '1.1' }, { amountAtomic: 1 }, { amountAtomic: String(2n ** 256n) }, { nonce: '8' }, { maxFeeAtomic: '1' }, { expiresAt: 'invalid' }, { expiresAt: new Date(Date.now() - 1).toISOString() }]) assert.throws(() => validateEvmPayment({ ...value, ...change }, descriptor, { ...asset, chainId: 1 }));
  for (const change of [{ sender: descriptor.ownerAddress }, { factoryData: '0x' }, { factory: target }, { paymaster: target }, { maxPriorityFeePerGas: '0xffffffffff' }, { callGasLimit: '0xffffffffff' }]) assert.throws(() => validateEvmPayment({ ...value, operation: { ...value.operation, ...change } }, descriptor, { ...asset, chainId: 1 }));
  await assert.rejects(signEvmTransfer(new Uint8Array(64).fill(2), descriptor, value, { ...asset, chainId: 1 }));
});
test('native transfers, allowances, batches and delegate calls cannot be signed as token payments', () => {
  for (const change of [{ value: 1n }, { operation: 1 }, { data: '0x095ea7b3' }, { to: target }]) {
    const callData = SafeAccountV0_3_0.createAccountCallDataSingleTransaction({ to: asset.contract, value: 0n, data: evmToken.encodeFunctionData('transfer', [target, 12345678901234567890n]), ...change });
    assert.throws(() => validateEvmPayment({ ...quote(), operation: { ...operation(), callData } }, descriptor, { ...asset, chainId: 1 }));
  }
});
test('signed envelope rejects forged signatures, altered validity and cross-network replay', async () => {
  const raw = await signEvmTransfer(seed, descriptor, quote(), { ...asset, chainId: 1 });
  const envelope = JSON.parse(toUtf8String(raw));
  for (const change of [{ chainId: 137 }, { expiresAt: new Date(Date.now() + 30000).toISOString() }, { from: descriptor.ownerAddress }]) {
    const changed = hexlify(toUtf8Bytes(JSON.stringify({ ...envelope, quote: { ...envelope.quote, ...change } })));
    assert.throws(() => inspectEvmTransfer(changed, network, descriptor));
  }
  envelope.quote.operation.signature = '0x' + '00'.repeat(77);
  assert.throws(() => inspectEvmTransfer(hexlify(toUtf8Bytes(JSON.stringify(envelope))), network, descriptor));
});
test('quotes read funds and estimate deployment at the same Safe address', async () => {
  for (const code of ['0x', '0x6000']) {
    const { gateway, calls } = provider({ eth_getCode: code });
    const value = await gateway.quote('ethereum', descriptor, { contract: asset.contract, to: target, amountAtomic: '1234567' });
    assert.equal(value.from, descriptor.address);
    assert.equal(!!value.operation.factory, code === '0x');
    assert.ok(calls.filter(item => item.method === 'eth_getBalance').every(item => item.params[0] === descriptor.address));
    assert.ok(calls.some(item => item.method === 'eth_estimateUserOperationGas' && item.params[0].sender === descriptor.address));
    validateEvmPayment(value, descriptor, { ...asset, chainId: 1 });
    assert.ok(!calls.some(item => /eth_send/.test(item.method)));
  }
  await assert.rejects(provider({ eth_chainId: '0x89' }).gateway.balance('ethereum', descriptor.address, asset.contract), { code: 'PAYMENT_NETWORK_MISMATCH' });
  await assert.rejects(provider({ eth_supportedEntryPoints: [] }).gateway.quote('ethereum', descriptor, { contract: asset.contract, to: target, amountAtomic: '1' }), { code: 'PAYMENT_NETWORK_MISMATCH' });
  await assert.rejects(provider({ eth_call: '0x0' }).gateway.quote('ethereum', descriptor, { contract: asset.contract, to: target, amountAtomic: '1' }), { code: 'INSUFFICIENT_FUNDS' });
  await assert.rejects(provider({ eth_getBalance: '0x0' }).gateway.quote('ethereum', descriptor, { contract: asset.contract, to: target, amountAtomic: '1' }), { code: 'INSUFFICIENT_GAS_FUNDS' });
  await assert.rejects(provider().gateway.balance('tron', descriptor.address, asset.contract), { code: 'INVALID_REQUEST' });
});
test('broadcast reuses exactly the signed operation and never signs an owner EOA transaction', async () => {
  const raw = await signEvmTransfer(seed, descriptor, quote(), { ...asset, chainId: 1 }), hash = evmTransactionHash(raw);
  const known = provider({ eth_getUserOperationByHash: { userOperation: operation() } });
  assert.equal((await known.gateway.broadcast('ethereum', descriptor, { raw })).hash, hash);
  assert.ok(!known.calls.some(item => item.method === 'eth_sendUserOperation'));
  await assert.rejects(provider({ eth_call: '0x8' }).gateway.broadcast('ethereum', descriptor, { raw }), { code: 'PAYMENT_TRACKING_REQUIRED' });
  const sending = provider({ eth_sendUserOperation: params => { assert.deepEqual(params[0], paymentOperation(inspectEvmTransfer(raw, network, descriptor).operation, true)); return hash; } });
  assert.equal((await sending.gateway.broadcast('ethereum', descriptor, { raw })).status, 'submitted');
  assert.ok(!sending.calls.some(item => item.method === 'eth_sendRawTransaction'));
});
test('canonical receipt must prove this Safe payment before reporting confirmation', async () => {
  const raw = await signEvmTransfer(seed, descriptor, quote(), { ...asset, chainId: 1 }), hash = evmTransactionHash(raw), tx = '0x' + 'ab'.repeat(32), blockHash = '0x' + 'cd'.repeat(32);
  assert.equal((await provider().gateway.status('ethereum', descriptor, hash, quote())).status, 'ambiguous');
  const event = userOperationEvents.encodeEventLog('UserOperationEvent', [hash, descriptor.address, ZeroAddress, 7n, true, 1n, 1n]);
  const transfer = evmToken.encodeEventLog('Transfer', [descriptor.address, target, BigInt(quote().amountAtomic)]);
  const receipt = { transactionHash: tx, status: '0x1', blockNumber: '0x10', blockHash, logs: [{ address: asset.contract, ...transfer }, { address: descriptor.entryPoint, ...event }] };
  const mock = { eth_getUserOperationReceipt: { userOpHash: hash, sender: descriptor.address, receipt }, eth_getTransactionReceipt: receipt, eth_getBlockByNumber: { hash: blockHash } };
  const result = await provider(mock).gateway.status('ethereum', descriptor, hash, quote());
  assert.equal(result.status, 'confirmed'); assert.equal(result.transactionHash, tx);
  assert.equal((await provider({ ...mock, eth_getBlockByNumber: { hash: tx } }).gateway.status('ethereum', descriptor, hash, quote())).status, 'pending');
  receipt.logs.shift();
  await assert.rejects(provider(mock).gateway.status('ethereum', descriptor, hash, quote()), { code: 'INVALID_REQUEST' });
});
test('journal reserves Safe nonce before network I/O and blocks a second authorization', async () => {
  let row, sent = 0;
  const db = { async transaction(fn) { return fn(this); }, async query(sql, args) {
    if (sql.startsWith('SELECT pg_')) return { rows: [] };
    if (sql.startsWith('SELECT')) return { rows: row ? [row] : [] };
    if (sql.startsWith('INSERT')) { row = { hash: args[4], payment: args[7], account_address: args[8], status: 'ambiguous', created_at: new Date().toISOString() }; return { rows: [] }; }
    if (sql.startsWith('UPDATE')) { row.status = args[args.length - 1]; return { rows: [] }; }
    throw Error(sql);
  } };
  const gateway = { async broadcast() { assert.ok(row); sent++; throw Error('connection lost'); }, async status() { return { hash: row.hash, status: 'ambiguous' }; } };
  const service = evmPaymentService(db, gateway), raw = await signEvmTransfer(seed, descriptor, quote(), { ...asset, chainId: 1 });
  assert.equal((await service.broadcast('account', 'ethereum', descriptor, { raw })).status, 'ambiguous');
  assert.equal(row.account_address, descriptor.address.toLowerCase()); assert.equal(sent, 1);
  assert.equal((await service.pending('account', 'ethereum', descriptor)).transfer.hash, evmTransactionHash(raw));
  const changed = quote(); changed.to = '0x' + '34'.repeat(20); changed.operation.callData = SafeAccountV0_3_0.createAccountCallDataSingleTransaction({ to: asset.contract, value: 0n, data: evmToken.encodeFunctionData('transfer', [changed.to, BigInt(changed.amountAtomic)]) });
  const other = await signEvmTransfer(seed, descriptor, changed, { ...asset, chainId: 1 });
  await assert.rejects(service.broadcast('account', 'ethereum', descriptor, { raw: other }), { code: 'PAYMENT_ALREADY_PENDING' }); assert.equal(sent, 1);
});
