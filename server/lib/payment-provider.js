import { SafeAccountV0_3_0, Erc7677Paymaster, calculateUserOperationMaxGasCost } from 'abstractionkit';
import { Interface } from 'ethers';
import { ApiError } from './errors.js';
import { paymentOperation, validatePaymentOperation, paymentToken } from '../../packages/wallet-core/src/payment.js';

export const userOperationEvents = new Interface(['event UserOperationEvent(bytes32 indexed userOpHash,address indexed sender,address indexed paymaster,uint256 nonce,bool success,uint256 actualGasCost,uint256 actualGasUsed)','event BeforeExecution()']);
const quantity=value=>`0x${BigInt(value).toString(16)}`;
const same=(a,b)=>typeof a==='string'&&typeof b==='string'&&a.toLowerCase()===b.toLowerCase();
export function paymentProvider(config, fetchImpl = fetch) {
  const rpc = (url, headers = {}) => async (method, params = []) => {
    try {
      const response=await fetchImpl(url,{redirect:'error',method:'POST',headers:{'Content-Type':'application/json',...headers},body:JSON.stringify({jsonrpc:'2.0',id:1,method,params}),signal:AbortSignal.timeout(20000)});
      const body=await response.json();
      if(response.status===429)throw new ApiError(503,'PAYMENT_PROVIDER_LIMITED','Payment service is busy; your payment has not been sent',{retryAfter:60});
      if(!response.ok || body.error)throw new ApiError(502,'PAYMENT_PROVIDER_UNAVAILABLE','Payment service could not confirm the request',{rpcCode:body.error?.code});
      return body.result;
    } catch(error) { if(error instanceof ApiError)throw error;throw new ApiError(502,'PAYMENT_PROVIDER_UNAVAILABLE','Payment service could not confirm the request'); }
  };
  const chain=rpc(config.rpcUrl),index=rpc(config.indexRpcUrl||config.rpcUrl),bundler=rpc(config.bundlerUrl,config.bundlerHeaders),paymaster=rpc(config.paymasterUrl,config.paymasterHeaders);
  const transport=request=>({request:({method,params})=>request(method,params)});
  let verifiedAt=0;
  const ensureNetwork=async()=>{
    if(Date.now()-verifiedAt<10000)return;
    const [chainId,bundlerChain,entries]=await Promise.all([chain('eth_chainId'),bundler('eth_chainId'),bundler('eth_supportedEntryPoints')]);
    if(BigInt(chainId)!==8453n||BigInt(bundlerChain)!==8453n||!Array.isArray(entries)||!entries.some(entry=>same(entry,config.entryPoint)))throw new ApiError(503,'PAYMENT_NETWORK_MISMATCH','Payment service is connected to another network');
    verifiedAt=Date.now();
  };
  return {
    chain,bundler,
    async balances(address,contracts) {
      if(BigInt(await chain('eth_chainId'))!==8453n)throw new ApiError(502,'PAYMENT_NETWORK_MISMATCH','Unexpected balance network');
      const block=await chain('eth_blockNumber');const result={};
      for(const contract of new Set(contracts.filter(Boolean)))result[contract.toLowerCase()]=BigInt(await chain('eth_call',[{to:contract,data:`0x70a08231${address.slice(2).toLowerCase().padStart(64,'0')}`},block]));
      return {blockNumber:BigInt(block).toString(),assets:result};
    },
    async quote(descriptor,payment) {
      await ensureNetwork();
      const smart=SafeAccountV0_3_0.initializeNewAccount([descriptor.ownerAddress],{c2Nonce:BigInt(descriptor.saltNonce),entrypointAddress:descriptor.entryPoint,safe4337ModuleAddress:descriptor.module,safeModuleSetupAddress:descriptor.setup});
      if(!same(smart.accountAddress,descriptor.address))throw new ApiError(400,'PAYMENT_ACCOUNT_MISMATCH','Account descriptor mismatch');
      const nonceCall=new Interface(['function getNonce(address,uint192) view returns(uint256)']).encodeFunctionData('getNonce',[descriptor.address,BigInt(payment.nonceKey)]);
      const nonce=BigInt(await chain('eth_call',[{to:descriptor.entryPoint,data:nonceCall},'latest']));
      const overrides={skipGasEstimation:true,nonce};
      if(config.paymasterProvider==='pimlico') { const price=await bundler('pimlico_getUserOperationGasPrice');if(price?.fast)Object.assign(overrides,{maxFeePerGas:BigInt(price.fast.maxFeePerGas),maxPriorityFeePerGas:BigInt(price.fast.maxPriorityFeePerGas)}); }
      const calls=[{to:payment.contract,value:0n,data:paymentToken.encodeFunctionData('transfer',[payment.recipient,BigInt(payment.amountAtomic)])}];
      if(payment.feeMode==='sponsored')calls.unshift({to:config.gasToken,value:0n,data:paymentToken.encodeFunctionData('approve',[config.paymasterAddress,0])});
      const operation=await smart.createUserOperation(calls,transport(chain),transport(bundler),overrides);
      if(await chain('eth_getCode',[descriptor.address,'latest'])==='0x') { operation.factory=descriptor.factoryAddress;operation.factoryData=descriptor.factoryData; }
      const sponsor=new Erc7677Paymaster(transport(paymaster),{chainId:8453n,provider:config.paymasterProvider});
      const context=payment.feeMode==='token'?{token:payment.feeToken}:config.sponsorshipPolicyId?{sponsorshipPolicyId:config.sponsorshipPolicyId}:{};
      const result=await sponsor.createPaymasterUserOperation(smart,operation,transport(bundler),context,{entrypoint:descriptor.entryPoint,resetApproval:true});
      const op=result.userOperation;const nativeMax=calculateUserOperationMaxGasCost(op);
      let fee=0n,maxFee=0n;
      if(payment.feeMode==='token') {
        if(!result.tokenQuote||!same(result.tokenQuote.token,payment.feeToken)||result.tokenQuote.exchangeRate<=0n)throw new ApiError(503,'GAS_TOKEN_UNSUPPORTED','The configured service cannot charge network costs in USDT');
        fee=(nativeMax*result.tokenQuote.exchangeRate+10n**18n-1n)/10n**18n;
        maxFee=result.tokenQuote.tokenCost*2n;
        if(fee>maxFee||maxFee>BigInt(payment.maxFeeAtomic))throw new ApiError(409,'GAS_LIMIT_EXCEEDED','Network cost exceeds the approved limit');
      }
      const policy={...payment,maxFeeAtomic:maxFee.toString(),paymaster:config.paymasterAddress};
      validatePaymentOperation(op,descriptor,policy);
      return {operation:paymentOperation(op,true),estimatedFeeAtomic:fee.toString(),maxFeeAtomic:maxFee.toString(),maxNativeAtomic:nativeMax.toString(),paymaster:config.paymasterAddress};
    },
    async submit(operation) { await ensureNetwork();return bundler('eth_sendUserOperation',[paymentOperation(operation,true),config.entryPoint]); },
    async receipt(hash) {
      const receipt=await bundler('eth_getUserOperationReceipt',[hash]);
      if(receipt) return receipt;
      return null;
    },
    async transactionReceipt(hash) { return chain('eth_getTransactionReceipt',[hash]); },
    async locateOperation(operation,descriptor,expiresAt,fromBlock) {
      if(BigInt(await index('eth_chainId'))!==8453n)throw new ApiError(502,'PAYMENT_NETWORK_MISMATCH','Unexpected receipt network');
      const latest=await chain('eth_getBlockByNumber',['latest',false]);
      let from=BigInt(operation.search_next_block??fromBlock),checkpointBlock=operation.search_checkpoint_block,checkpointHash=operation.search_checkpoint_hash,reset=false;
      if(checkpointBlock!=null) {
        const header=await chain('eth_getBlockByNumber',[quantity(checkpointBlock),false]);
        if(!header||!same(header.hash,checkpointHash)) {from=BigInt(fromBlock);checkpointBlock=null;checkpointHash=null;reset=true;}
      }
      const expiry=Date.parse(expiresAt);
      const stop=!reset&&operation.expiry_observed_block!=null?BigInt(operation.expiry_observed_block):Date.now()>=expiry&&Number(BigInt(latest.timestamp))*1000>=expiry?BigInt(latest.number):null;
      const target=stop??BigInt(latest.number);
      const proposed=from+BigInt(config.paymentReconcileRange)-1n;
      const to=proposed<target?proposed:target;
      if(from<=to) {
        const [header,backup]=await Promise.all([chain('eth_getBlockByNumber',[quantity(to),false]),index('eth_getBlockByNumber',[quantity(to),false])]);
        if(!header||!backup||!same(header.hash,backup.hash))throw new ApiError(502,'RECEIPT_SOURCE_BEHIND','Receipt source is still synchronizing');
        const logs=await index('eth_getLogs',[{address:config.entryPoint,fromBlock:quantity(from),toBlock:quantity(to),topics:[userOperationEvents.getEvent('UserOperationEvent').topicHash,operation.user_operation_hash]}]);
        if(!Array.isArray(logs))throw new ApiError(502,'INVALID_PAYMENT_RECEIPT','Receipt search could not be verified');
        if(logs.length)return {transactionHash:logs[0].transactionHash};
        const after=await chain('eth_getBlockByNumber',[quantity(to),false]);
        if(!after||!same(after.hash,header.hash))throw new ApiError(502,'RECEIPT_REORGANIZED','Receipt search is being repeated');
        from=to+1n;checkpointBlock=to.toString();checkpointHash=header.hash;
      }
      let absent=false;
      if(stop!=null&&from>stop&&Date.now()>=expiry) {
        const final=await chain('eth_getBlockByNumber',['finalized',false]);
        const checkpoint=checkpointBlock==null?null:await chain('eth_getBlockByNumber',[quantity(checkpointBlock),false]);
        absent=!!final&&!!checkpoint&&same(checkpoint.hash,checkpointHash)&&BigInt(final.number)>=stop&&Number(BigInt(final.timestamp))*1000>expiry;
      }
      return {absent,nextBlock:from.toString(),checkpointBlock,checkpointHash,stopBlock:stop?.toString()??null};
    },
  };
}

export function inspectOperationReceipt(receipt,intent,operation,config) {
  if(!receipt||!same(receipt.transactionHash,operation.transaction_hash||receipt.transactionHash)||receipt.status!=='0x1'||!Array.isArray(receipt.logs))throw new ApiError(502,'INVALID_PAYMENT_RECEIPT','Payment receipt could not be verified');
  let start=0,position=-1,decoded;
  for(let index=0;index<receipt.logs.length;index++) {
    const log=receipt.logs[index];if(!same(log.address,config.entryPoint))continue;
    let event;try{event=userOperationEvents.parseLog(log);}catch{continue;}
    if(event?.name==='BeforeExecution')start=index+1;
    if(event?.name==='UserOperationEvent') {
      if(same(event.args.userOpHash,operation.user_operation_hash)) {position=index;decoded=event;break;}
      start=index+1;
    }
  }
  if(position<0||!same(decoded.args.sender,intent.sender)||decoded.args.nonce!==BigInt(operation.nonce)||!same(decoded.args.paymaster,intent.quote.paymaster))throw new ApiError(502,'INVALID_PAYMENT_RECEIPT','User operation is missing from its transaction');
  const movements=[];
  for(const log of receipt.logs.slice(start,position)) {
    if(!same(log.address,intent.contract)&&!same(log.address,intent.fee_token||config.gasToken))continue;
    let event;try{event=paymentToken.parseLog(log);}catch{continue;}
    if(event?.name==='Transfer')movements.push({logIndex:Number(BigInt(log.logIndex??log.index)),contract:log.address.toLowerCase(),from:event.args.from.toLowerCase(),to:event.args.to.toLowerCase(),amount:event.args.value});
  }
  const payments=movements.filter(event=>same(event.contract,intent.contract)&&same(event.from,intent.sender)&&same(event.to,intent.recipient)&&event.amount===BigInt(intent.amount_atomic));
  if(decoded.args.success&&payments.length!==1)throw new ApiError(502,'INVALID_PAYMENT_RECEIPT','The receipt does not contain the approved payment');
  const gas=movements.filter(event=>same(event.contract,intent.fee_token||config.gasToken)&&(same(event.from,intent.sender)||same(event.to,intent.sender))&&!payments.some(payment=>payment.logIndex===event.logIndex));
  const actualFee=gas.reduce((sum,event)=>sum+(same(event.from,intent.sender)?event.amount:-event.amount),0n);
  if(actualFee<0n||(!decoded.args.success&&payments.length))throw new ApiError(502,'INVALID_PAYMENT_RECEIPT','Payment accounting could not be verified');
  return {success:decoded.args.success,actualNativeAtomic:decoded.args.actualGasCost.toString(),actualFeeAtomic:actualFee.toString(),paymentLogIndex:payments[0]?.logIndex,gasLogIndexes:gas.map(event=>event.logIndex),accountingState:actualFee<=BigInt(intent.quote.maxFeeAtomic)&&decoded.args.actualGasCost<=BigInt(intent.quote.maxNativeAtomic)?'settled':'review'};
}
