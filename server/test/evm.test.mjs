import test from 'node:test';
import assert from 'node:assert/strict';
import { HDNodeWallet, Wallet, keccak256 } from 'ethers';
import { STABLECOIN_NETWORKS, evmNetworks, stablecoinNetwork } from '../../config/stablecoin-networks.js';
import { evmTransaction, signEvmTransfer, inspectEvmTransfer, evmToken } from '../../packages/wallet-core/src/evm.js';
import { evmGateway } from '../lib/evm.js';
import { evmPaymentService } from '../lib/evm-payments.js';
import AssetCatalog from '../../web/src/lib/account/AssetCatalog.js';
const seed = new Uint8Array(64).fill(1), path = "m/44'/60'/0'/0/0", owner = HDNodeWallet.fromSeed(seed).derivePath(path);
const descriptor = { ownerAddress: owner.address, derivationPath: path };
const network = stablecoinNetwork('ethereum'), asset = network.assets.find(asset => asset.symbol === 'USDT');
const target = '0x'+'12'.repeat(20);
const quote = () => ({ network: 'ethereum', chainId: 1, from: owner.address, to: target, contract: asset.contract, symbol: 'USDT', decimals: 6, amountAtomic: '12345678901234567890', nonce: 7, gasLimit: '80000', gasPrice: '1000000000', maxFeeAtomic: '80000000000000', expiresAt: new Date(Date.now()+120000).toISOString() });
function provider(overrides = {}) {
  const calls = [];
  const fetchImpl = async (url, options) => {
    const { method,params } = JSON.parse(options.body); calls.push({ url,method,params });
    const values = { eth_chainId: '0x1', eth_blockNumber: '0x100', eth_call: '0xffffffffffffffffffffffffffff', eth_getBalance: '0xde0b6b3a7640000', eth_getTransactionCount: '0x7', eth_gasPrice: '0x3b9aca00', eth_estimateGas: '0x10000', eth_getTransactionByHash: null, eth_getTransactionReceipt: null, eth_getLogs: [], ...overrides };
    if (!Object.hasOwn(values,method)) throw Error('Unexpected RPC '+method);
    const result = typeof values[method] === 'function' ? values[method](params) : values[method];
    return { ok: true, json: async () => ({ result }) };
  };
  return { calls, gateway: evmGateway({ fetchImpl }) };
}
test('registry separates native USDC, native USDT and USDT0 and excludes Base USDT', () => {
  assert.equal(STABLECOIN_NETWORKS.length,53);
  assert.deepEqual(stablecoinNetwork('base').assets.map(asset => asset.symbol),['USDC']);
  assert.deepEqual(evmNetworks.map(network => network.chainId).sort((a,b)=>a-b),[1,137,42161,43114]);
  const catalog = new AssetCatalog();
  assert.equal(catalog.popular.length,1);
  const bridge = catalog.all.find(asset => asset.platform==='base' && asset.symbol==='USDT');
  assert.equal(bridge.addable,false);assert.equal(bridge.name,'Bridged USDT');assert.equal(bridge.supported,true);
  for (const network of evmNetworks) for (const asset of network.assets) assert.equal(catalog.get(`${asset.contract}@${network.id}`).chainId, network.chainId);
});
test('local signing binds account, token, recipient, chain, precision, nonce and maximum cost', async () => {
  const raw = await signEvmTransfer(seed,descriptor,quote(),{ ...asset,chainId:1 });
  const payment = inspectEvmTransfer(raw,network,owner.address);
  assert.equal(payment.amountAtomic,'12345678901234567890');assert.equal(payment.to.toLowerCase(),target);assert.equal(payment.hash,keccak256(raw));
  for (const change of [{chainId:137},{network:'base'},{contract:stablecoinNetwork('base').assets[0].contract},{from:target},{to:owner.address},{to:asset.contract},{to:'0x'+'0'.repeat(40)},{amountAtomic:'-1'},{amountAtomic:'1.1'},{amountAtomic:1},{amountAtomic:String(2n**256n)},{nonce:-1},{nonce:1.5},{gasLimit:'1000001'},{gasPrice:'0'},{maxFeeAtomic:'1'},{expiresAt:'invalid'},{expiresAt:new Date(Date.now()-1).toISOString()}]) assert.throws(()=>evmTransaction({...quote(),...change},descriptor,{...asset,chainId:1}));
  await assert.rejects(signEvmTransfer(new Uint8Array(64).fill(2),descriptor,quote(),{...asset,chainId:1}));
});
test('broadcast rejects wrong owners, chains, approval calldata and native transfers', async () => {
  const transaction=evmTransaction(quote(),descriptor,{...asset,chainId:1});
  for(const changes of [{value:1n},{chainId:137},{to:target},{data:'0x095ea7b3'},{gasLimit:1000001n}]) { const raw=await owner.signTransaction({...transaction,...changes});assert.throws(()=>inspectEvmTransfer(raw,network,owner.address)); }
  const foreign=await Wallet.createRandom().signTransaction(transaction);assert.throws(()=>inspectEvmTransfer(foreign,network,owner.address));
});
test('quotes use verified RPC identity, actual balance and bounded native gas', async () => {
  const { gateway,calls } = provider();
  const value=await gateway.quote('ethereum',owner.address,{contract:asset.contract,to:target,amountAtomic:'1234567'});
  assert.equal(value.chainId,1);assert.equal(value.symbol,'USDT');assert.equal(BigInt(value.maxFeeAtomic),BigInt(value.gasLimit)*BigInt(value.gasPrice));
  assert.ok(calls.every(call=>new URL(call.url).protocol==='https:'));assert.ok(!calls.some(call=>call.method==='eth_sendRawTransaction'));
  await assert.rejects(provider({eth_chainId:'0x89'}).gateway.balance('ethereum',owner.address,asset.contract),{code:'PAYMENT_NETWORK_MISMATCH'});
  await assert.rejects(provider({eth_call:'0x0'}).gateway.quote('ethereum',owner.address,{contract:asset.contract,to:target,amountAtomic:'1'}),{code:'INSUFFICIENT_FUNDS'});
  await assert.rejects(provider({eth_getBalance:'0x0'}).gateway.quote('ethereum',owner.address,{contract:asset.contract,to:target,amountAtomic:'1'}),{code:'INSUFFICIENT_GAS_FUNDS'});
  await assert.rejects(gateway.balance('tron',owner.address,asset.contract),{code:'INVALID_REQUEST'});
});
test('broadcast is idempotent for the same signed bytes and rejects a stale nonce', async () => {
  const raw=await signEvmTransfer(seed,descriptor,quote(),{...asset,chainId:1});
  const known=provider({eth_getTransactionByHash:{from:owner.address}});
  assert.equal((await known.gateway.broadcast('ethereum',owner.address,{raw})).hash,keccak256(raw));assert.ok(!known.calls.some(call=>call.method==='eth_sendRawTransaction'));
  await assert.rejects(provider({eth_getTransactionCount:'0x8'}).gateway.broadcast('ethereum',owner.address,{raw}),{code:'PAYMENT_TRACKING_REQUIRED'});
  const sending=provider({eth_sendRawTransaction:()=>keccak256(raw)});
  assert.equal((await sending.gateway.broadcast('ethereum',owner.address,{raw})).status,'submitted');
});
test('missing or reorganized receipts never report delivery', async () => {
  const hash='0x'+'ab'.repeat(32), tx={from:owner.address,to:asset.contract,input:evmToken.encodeFunctionData('transfer',[target,1n])};
  assert.equal((await provider().gateway.status('ethereum',owner.address,hash)).status,'ambiguous');
  assert.equal((await provider({eth_getTransactionByHash:tx}).gateway.status('ethereum',owner.address,hash)).status,'pending');
  const receipt={status:'0x1',blockNumber:'0x10',blockHash:hash,logs:[]};
  assert.equal((await provider({eth_getTransactionByHash:tx,eth_getTransactionReceipt:receipt,eth_getBlockByNumber:{hash:'0x'+'cd'.repeat(32)}}).gateway.status('ethereum',owner.address,hash)).status,'pending');
  assert.equal((await provider({eth_getTransactionByHash:tx,eth_getTransactionReceipt:receipt,eth_getBlockByNumber:{hash}}).gateway.status('ethereum',owner.address,hash)).status,'reverted');
  const event=evmToken.encodeEventLog('Transfer',[owner.address,target,1n]);receipt.logs=[{address:asset.contract,...event}];
  assert.equal((await provider({eth_getTransactionByHash:tx,eth_getTransactionReceipt:receipt,eth_getBlockByNumber:{hash}}).gateway.status('ethereum',owner.address,hash)).status,'confirmed');
});
test('pending journal reserves a nonce before broadcast and blocks a second payment', async () => {
  let row, sent=0;
  const db={async transaction(fn){return fn(this);},async query(sql,args){
    if(sql.startsWith('SELECT pg_'))return {rows:[]};
    if(sql.startsWith('SELECT'))return {rows:row?[row]:[]};
    if(sql.startsWith('INSERT')){row={hash:args[4],payment:args[7],status:'ambiguous',created_at:new Date().toISOString()};return {rows:[]};}
    if(sql.startsWith('UPDATE')){row.status=args[args.length-1];return {rows:[]};}
    throw Error(sql);
  }};
  const gateway={async broadcast(){assert.ok(row);sent++;throw Error('connection lost');},async status(){return {hash:row.hash,status:'ambiguous'};}};
  const service=evmPaymentService(db,gateway),raw=await signEvmTransfer(seed,descriptor,quote(),{...asset,chainId:1});
  assert.equal((await service.broadcast('account','ethereum',owner.address,{raw})).status,'ambiguous');assert.equal(sent,1);
  assert.equal((await service.pending('account','ethereum',owner.address)).transfer.hash,keccak256(raw));
  const other=await signEvmTransfer(seed,descriptor,{...quote(),to:'0x'+'34'.repeat(20)},{...asset,chainId:1});
  await assert.rejects(service.broadcast('account','ethereum',owner.address,{raw:other}),{code:'PAYMENT_ALREADY_PENDING'});assert.equal(sent,1);
});
