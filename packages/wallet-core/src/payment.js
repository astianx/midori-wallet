import { Interface, getAddress, verifyTypedData } from 'ethers';
import { SafeAccountV0_3_0, createUserOperationHash } from 'abstractionkit';

export const paymentToken = new Interface(['function transfer(address to,uint256 amount) returns(bool)','function approve(address spender,uint256 amount) returns(bool)','event Transfer(address indexed from,address indexed to,uint256 value)']);
const multisend = new Interface(['function multiSend(bytes transactions)']);
const numbers = ['nonce','callGasLimit','verificationGasLimit','preVerificationGas','maxFeePerGas','maxPriorityFeePerGas','paymasterVerificationGasLimit','paymasterPostOpGasLimit'];
const fields = ['sender',...numbers,'factory','factoryData','callData','paymaster','paymasterData','signature'];
const check = (condition, message) => { if (!condition) throw Object.assign(new Error(message), { code: 'PAYMENT_POLICY_REJECTED' }); };
const equalAddress = (a,b) => typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();
export const paymentCanonical = value => Array.isArray(value) ? `[${value.map(paymentCanonical).join(',')}]` : value && typeof value === 'object' ? `{${Object.keys(value).sort().map(key=>`${JSON.stringify(key)}:${paymentCanonical(value[key])}`).join(',')}}` : JSON.stringify(value);

export function paymentOperation(value, wire = false) {
  check(value && typeof value === 'object' && Object.keys(value).every(key=>fields.includes(key)), 'Unexpected operation field');
  const result = {};
  for (const field of fields) {
    if (value[field] == null) { if (['factory','factoryData','paymaster','paymasterData'].includes(field)) result[field] = null; continue; }
    if (numbers.includes(field)) {
      check(/^(?:0x[0-9a-fA-F]+|[0-9]+)$/.test(String(value[field])), 'Invalid operation quantity');
      const number=BigInt(value[field]);check(number>=0n && number<2n**256n,'Invalid operation quantity');
      result[field]=wire ? `0x${number.toString(16)}` : number;
    } else { check(typeof value[field] === 'string' && /^0x[0-9a-fA-F]*$/.test(value[field]) && value[field].length % 2 === 0,'Invalid operation data'); result[field]=value[field]; }
  }
  check(['sender','nonce','callData',...numbers.slice(1,6)].every(key=>result[key]!=null),'Incomplete operation');
  check(getAddress(result.sender) && result.callData.length<=262144,'Invalid operation sender');
  return result;
}

function flattenCall(call, depth = 0) {
  check(depth<4 && BigInt(call.value)===0n,'Unexpected value transfer');
  if (Number(call.operation || 0)===0) return [call];
  check(Number(call.operation)===1 && equalAddress(call.to,SafeAccountV0_3_0.DEFAULT_MULTISEND_CONTRACT_ADDRESS),'Unexpected delegate call');
  const data=multisend.decodeFunctionData('multiSend',call.data)[0].slice(2);let offset=0;const calls=[];
  while(offset<data.length) {
    check(offset+170<=data.length,'Invalid batch');
    const operation=Number.parseInt(data.slice(offset,offset+2),16),to=`0x${data.slice(offset+2,offset+42)}`,value=BigInt(`0x${data.slice(offset+42,offset+106)}`),length=BigInt(`0x${data.slice(offset+106,offset+170)}`);
    check(length<=131072n && offset+170+Number(length)*2<=data.length,'Invalid batch size');
    const payload=`0x${data.slice(offset+170,offset+170+Number(length)*2)}`;offset+=170+Number(length)*2;
    calls.push(...flattenCall({operation,to,value,data:payload},depth+1));check(calls.length<=4,'Too many payment calls');
  }
  return calls;
}

export function validatePaymentOperation(value, descriptor, payment) {
  const op=paymentOperation(value);check(descriptor.chainId===8453 && equalAddress(op.sender,descriptor.address),'Unexpected sender or network');
  check(!op.factory || (equalAddress(op.factory,descriptor.factoryAddress) && op.factoryData===descriptor.factoryData),'Unexpected account deployment');
  check((!op.factory) === (!op.factoryData),'Incomplete factory');
  const [outer]=SafeAccountV0_3_0.decodeAccountCallData(op.callData);const calls=flattenCall(outer);
  const transfers=[];const approvals=[];
  for(const call of calls) {
    let decoded;try{decoded=paymentToken.parseTransaction({data:call.data});}catch{check(false,'Unexpected account call');}
    check(decoded,'Unknown account call');
    if(decoded.name==='transfer') {check(equalAddress(call.to,payment.contract) && equalAddress(decoded.args.to,payment.recipient) && decoded.args.amount===BigInt(payment.amountAtomic),'Payment changed after confirmation');transfers.push(call);}
    else {check(decoded.name==='approve' && ['token','sponsored'].includes(payment.feeMode) && equalAddress(call.to,payment.feeToken || '0xfde4c96c8593536e31f229ea8f37b2ada2699bb2') && equalAddress(decoded.args.spender,payment.paymaster),'Unexpected token allowance');approvals.push(decoded.args.amount);}
  }
  check(payment.nonceKey != null && (op.nonce >> 64n) === BigInt(payment.nonceKey), 'Unexpected payment nonce lane');
  check(transfers.length===1 && BigInt(payment.amountAtomic)>0n,'Exactly one payment is required');
  check(equalAddress(op.paymaster,payment.paymaster),'Unexpected paymaster');
  check(op.maxFeePerGas>0n && op.maxPriorityFeePerGas<=op.maxFeePerGas && op.callGasLimit>0n,'Invalid gas limits');
  if(payment.feeMode==='token') {
    check(approvals.length===2 && approvals[0]===0n && approvals[1]>0n && approvals[1]<=BigInt(payment.maxFeeAtomic),'Gas allowance exceeds the approved limit');
  } else check(payment.feeMode==='sponsored' && approvals.length===1 && approvals[0]===0n && BigInt(payment.maxFeeAtomic)===0n,'Unexpected gas mode');
  return op;
}

export function paymentSigningData(value, descriptor, expiresAt) {
  return SafeAccountV0_3_0.getUserOperationEip712Data(paymentOperation(value),BigInt(descriptor.chainId),{validAfter:0n,validUntil:BigInt(Math.floor(Date.parse(expiresAt)/1000)),entrypointAddress:descriptor.entryPoint,safe4337ModuleAddress:descriptor.module});
}
export function paymentHash(value,descriptor) { return createUserOperationHash(paymentOperation(value),descriptor.entryPoint,BigInt(descriptor.chainId)); }
export function paymentSignature(value,descriptor,expiresAt,signature) {
  const typed=paymentSigningData(value,descriptor,expiresAt);
  check(equalAddress(verifyTypedData(typed.domain,typed.types,typed.messageValue,signature),descriptor.ownerAddress),'The signature does not control this account');
  return SafeAccountV0_3_0.formatEip712SignaturesToUseroperationSignature([descriptor.ownerAddress],[signature],{validAfter:0n,validUntil:BigInt(Math.floor(Date.parse(expiresAt)/1000))});
}
