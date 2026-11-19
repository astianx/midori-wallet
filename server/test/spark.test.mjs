import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { generateKeyPairSync, verify } from 'node:crypto';
import { lightningDetails, validateSparkAddress, normalizeSparkTransfer, openSparkSession, SPARK_PATH } from '../../packages/wallet-core/src/spark.js';
import { fiatCredentials, changellyFiatClient, fiatCheckoutUrl } from '../lib/changelly-fiat.js';
const requireCore = createRequire(new URL('../../packages/wallet-core/package.json',import.meta.url));
const { sign,encode } = await import(requireCore.resolve('@tetherto/wdk-utils'));
const { SparkProto } = await import(requireCore.resolve('@buildonspark/spark-sdk/types'));
const sparkAddress = 'spark1pgssyp5hy794jwrc8gz3eavz5ucaangm3w26ewwv7dtyw68mf2vgptgkt5uc3g';
const identity = '020697278b5938783a051cf582a731decd1b8b95acb9ccf3564768fb4a9880ad16';
test('Spark public receiving descriptor opens offline without requesting a signing seed',async()=>{
  const {default:SparkController}=await import('../../web/src/lib/account/SparkController.js');
  let unlocked=true;const descriptor={address:sparkAddress,identityKey:identity,network:'MAINNET',derivationPath:SPARK_PATH};
  const controller=new SparkController({unlocked:()=>unlocked,online:()=>false,cachedDescriptor:()=>descriptor,seed:()=>{throw Error('Signing seed must not be requested');}});
  assert.equal((await controller.descriptor()).address,sparkAddress);
  await assert.rejects(controller.read('balance'),{code:'NETWORK_UNCONFIRMED'});
  unlocked=false;await assert.rejects(controller.descriptor(),{code:'ACCOUNT_LOCKED'});
});
test('Fiat presentation distinguishes uncertain orders and the Spark Bitcoin bridge',async()=>{
  const { rampStatus,rampNetwork,isPendingRampOrder }=await import('../../web/src/lib/account/ramp-presentation.js');
  assert.equal(rampStatus({status:'ambiguous'},value=>value),'Checking');
  assert.equal(isPendingRampOrder({status:'ambiguous'}),true);
  assert.equal(rampNetwork('PRODUCTION','spark'),'Bitcoin ↔ Spark');
});
test('Fiat metadata cannot substitute an unspecified USDT for the wallet bridged USDT',async()=>{
  const {changellyFiatService}=await import('../lib/changelly-fiat-service.js');
  const db={query:async sql=>({rows:sql.startsWith('SELECT id,descriptor')?[{id:'account',descriptor:{address:'0x'+'12'.repeat(20)}}]:[{contract:'0xfde4c96c8593536e31f229ea8f37b2ada2699bb2',symbol:'USDT',decimals:6}]})};
  const support=[{providerCode:'moonpay',supportedFlows:['buy','sell']}];
  const provider={countries:async()=>[{code:'CO',name:'Colombia',providers:support}],currencies:async()=>[{type:'crypto',ticker:'USDTBASE',network:'base',precision:'6',providers:support},{type:'fiat',ticker:'USD',precision:'2',providers:support}]};
  const service=changellyFiatService(db,{changellyFiatEnabled:true,changellyFiatEnvironment:'PRODUCTION'},provider);
  assert.deepEqual((await service.options('wallet','account',{kind:'buy',cryptoCurrency:'USDT',network:'base'})).countries,[]);
});
function invoice({network='bitcoin',msat='1000',expiry=3600}={}) {
  const value = sign({network,millisatoshis:msat,timestamp:1700000000,tags:[{tagName:'payment_hash',data:'01'.repeat(32)},{tagName:'description',data:'Midori test'},{tagName:'expiry',data:expiry}]},new Uint8Array(32).fill(1));
  assert.equal(value.success,true); const encoded = encode(value.data); assert.equal(encoded.success,true); return encoded.data;
}
test('Lightning validates network, whole satoshis, checksum and invoice expiry',()=>{
  const data = lightningDetails(invoice(),1700000000000); assert.equal(data.amountAtomic,'1'); assert.equal(data.memo,'Midori test');
  assert.throws(()=>lightningDetails(invoice(),1700003600000),{code:'INVOICE_EXPIRED'});
  assert.throws(()=>lightningDetails(invoice({network:'testnet'}),1700000000000),{code:'INVALID_INVOICE'});
  assert.throws(()=>lightningDetails(invoice({msat:'1'}),1700000000000),{code:'INVALID_INVOICE'});
  assert.throws(()=>lightningDetails(invoice()+'x',1700000000000),{code:'INVALID_INVOICE'});
  assert.equal(validateSparkAddress(sparkAddress),true); assert.equal(validateSparkAddress(sparkAddress+' '),false);
});
test('Spark history attributes each participant and change instead of the aggregate transfer',()=>{
  const own=Buffer.from(identity,'hex'),other=Buffer.from('02'+'12'.repeat(32),'hex');
  const leaf=(value,sender,receiver)=>({leaf:{value},transferSenderId:sender,transferReceiverId:receiver});
  const transfer=SparkProto.Transfer.fromPartial({id:'11111111-1111-4111-8111-111111111111',status:1,totalValue:99000,senderIdentityPublicKey:other,receiverIdentityPublicKey:other,senders:[{id:'s',identityPublicKey:other}],receivers:[{id:'mine',identityPublicKey:own,status:6},{id:'other',identityPublicKey:other,status:0}],leaves:[leaf(17,'s','mine'),leaf(999,'s','other')],createdTime:new Date()});
  const incoming=normalizeSparkTransfer(transfer,identity); assert.equal(incoming.amountAtomic,'17'); assert.equal(incoming.status,'COMPLETED'); assert.equal(incoming.incoming,true);
  transfer.senders[0].identityPublicKey=own; transfer.status=5;
  const outgoing=normalizeSparkTransfer(transfer,identity); assert.equal(outgoing.amountAtomic,'999'); assert.equal(outgoing.incoming,false); assert.equal(outgoing.status,'COMPLETED');
});
test('Spark adapter binds the approved deposit credit and pays withdrawal fees separately',async()=>{
  const calls=[]; const account={path:SPARK_PATH,getAddress:async()=>sparkAddress,getIdentityKey:async()=>identity,withdraw:async value=>{calls.push(value);return{id:'request'};},cleanupConnections:async()=>{calls.push('cleanup');},_wallet:{claimStaticDeposit:async value=>{calls.push(value);return{transferId:'transfer'};}}};
  const session=await openSparkSession(new Uint8Array(64),{managerFactory:()=>({getAccount:async()=>account,dispose(){calls.push('disposed');}})});
  await session.execute('withdraw',{amountAtomic:'12345678',to:'own',maxFeeAtomic:'100'}); assert.equal(calls[0].deductFeeFromWithdrawalAmount,false); assert.equal(calls[0].amountSats,12345678);
  await session.execute('claim',{amountAtomic:'900',transactionId:'a'.repeat(64),signature:'reviewed'}); assert.equal(calls[1].creditAmountSats,900); assert.equal(calls[1].sspSignature,'reviewed');
  await session.dispose(); await session.dispose(); assert.deepEqual(calls.slice(2),['cleanup','disposed']); await assert.rejects(session.execute('withdraw',{amountAtomic:'1'}),{code:'ACCOUNT_LOCKED'});
});
test('Changelly Fiat signs full URLs and exact JSON with separate complete credentials',async()=>{
  const pair=generateKeyPairSync('rsa',{modulusLength:2048}),pem=pair.privateKey.export({format:'pem',type:'pkcs8'}).toString(),apiKey='1'.repeat(64);
  assert.throws(()=>fiatCredentials(Buffer.from(pem.slice(0,800)).toString('base64'),apiKey),/complete RSA/);
  const calls=[],client=changellyFiatClient({changellyFiatApiSecret:Buffer.from(pem).toString('base64'),changellyFiatApiKey:apiKey},async(url,options)=>{assert.equal(verify('sha256',Buffer.from(url+(options.body||'{}')),pair.publicKey,Buffer.from(options.headers['X-Api-Signature'],'base64')),true);assert.equal(options.headers['X-Api-Key'],apiKey);calls.push({url,options});return{ok:true,json:async()=>[]};});
  await client.offers('sell',{amountFrom:'0.12345678',country:'CO'}); assert.ok(calls[0].url.includes('/v1/sell/offers?amountFrom=0.12345678')); assert.equal(calls[0].options.body,undefined);
  await client.create('sell',{amountFrom:JSON.rawJSON('0.12345678')});assert.equal(calls[1].options.body,'{"amountFrom":0.12345678}');
  assert.equal(fiatCheckoutUrl('https://buy.moonpay.com/changelly?order=one'),'https://buy.moonpay.com/changelly?order=one');
  assert.equal(fiatCheckoutUrl('https://changelly.com/buy','STAGING'),'https://changelly.com/buy');
  assert.equal(fiatCheckoutUrl('https://buy.moonpay.com/changelly','STAGING'),null);
  assert.equal(fiatCheckoutUrl('https://changelly.com/sell','PRODUCTION'),null);
  for(const value of ['javascript:alert(1)','https://buy.moonpay.com.attacker.invalid','https://user:password@buy.moonpay.com'])assert.equal(fiatCheckoutUrl(value),null);
});
