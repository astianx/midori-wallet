import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { JsonRpcProvider, Contract, Wallet, Interface, parseUnits } from 'ethers';
import { erc20Address, sudoMintTokens } from './node_modules/@pimlico/mock-paymaster/_esm/helpers/erc20-utils.js';
import { getSingletonPaymaster07Address } from './node_modules/@pimlico/mock-paymaster/_esm/constants.js';
import { createWalletSession, amountAfterFeeReserve } from '../../packages/wallet-core/src/index.js';
import { PUBLIC_TEST_VECTOR } from '../../packages/wallet-core/src/testing.js';

const root=fileURLToPath(new URL('../../',import.meta.url));
const config=JSON.parse(fs.readFileSync(path.join(root,'.local/f1/local-chain/state.json'),'utf8'));
assert.equal(config.chainId,31337);
const provider=new JsonRpcProvider(config.rpcUrl);provider.pollingInterval=100;
assert.equal(BigInt(await provider.send('eth_chainId',[])),31337n);
const sponsorOwner=(await provider.send('eth_accounts',[]))[1];
const paymasterAddress=getSingletonPaymaster07Address(sponsorOwner);
const cap=parseUnits('100',18);
const session=await createWalletSession(PUBLIC_TEST_VECTOR.mnemonic,{chainId:31337,index:60,provider:config.rpcUrl,bundlerUrl:config.bundlerUrl,paymasterUrl:config.paymasterUrl,feeMode:'token',paymasterAddress,paymasterToken:erc20Address,transferMaxFee:cap});
const address=session.descriptor.address;
const recipient='0x2222222222222222222222222222222222222222';
assert.equal(await provider.send('eth_getCode',[address,'latest']),'0x','Restart the isolated local chain before repeating this first-deployment proof');
const erc20=new Interface(['function balanceOf(address) view returns (uint256)','function decimals() view returns (uint8)','event Transfer(address indexed from,address indexed to,uint256 value)']);
const readBalance=async(token,account,block='latest')=>BigInt(await provider.send('eth_call',[{to:token,data:erc20.encodeFunctionData('balanceOf',[account])},block]));
const readEth=async(account,block='latest')=>BigInt(await provider.send('eth_getBalance',[account,block]));
assert.equal(BigInt(await provider.send('eth_call',[{to:erc20Address,data:erc20.encodeFunctionData('decimals')},'latest'])),18n);
await sudoMintTokens({amount:parseUnits('10000',18),to:address,anvilRpc:config.rpcUrl});
const funded=new Wallet('0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80',provider);
const usdc=new Contract(config.assets.USDC.contract,config.abi,funded);await (await usdc.mint(address,100000000n)).wait();
const evidence={testedAt:new Date().toISOString(),network:'local Anvil, NOT Base',chainId:31337,feeMode:'token',feeAsset:{contract:erc20Address,decimals:18,label:'Upstream mock-paymaster ERC-20, not real USDT and not the six-decimal Base USDT'},descriptor:session.descriptor,paymasterAddress,operations:[]};
async function bundler(method,params){const response=await fetch(config.bundlerUrl,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:1,method,params})});const body=await response.json();if(body.error)throw Error(JSON.stringify(body.error));return body.result;}
async function transfer(token,amount,scenario,reserve=null){
 const senderBefore=await readBalance(token,address);const receiverBefore=await readBalance(token,recipient);const feeBefore=await readBalance(erc20Address,address);
 assert.equal(await readEth(address),0n);assert.equal(await readEth(session.descriptor.ownerAddress),0n);
 const deployedBefore=await provider.send('eth_getCode',[address,'latest'])!=='0x';
 const quote=await session.quoteTransfer({token,recipient,amount});assert.ok(quote.fee>0n);assert.ok(quote.fee<=cap);
 const sent=await session.submitTransfer({token,recipient,amount},{allowLocalBroadcast:true});
 let receipt;for(let i=0;i<120;i++){receipt=await bundler('eth_getUserOperationReceipt',[sent.hash]);if(receipt)break;await new Promise(resolve=>setTimeout(resolve,250));}
 assert.equal(receipt?.success,true);
 const block=receipt.receipt.blockNumber;
 const senderAfter=await readBalance(token,address,block);const receiverAfter=await readBalance(token,recipient,block);const feeAfter=await readBalance(erc20Address,address,block);
 const feeDebit=feeBefore-feeAfter-(token.toLowerCase()===erc20Address.toLowerCase()?amount:0n);
 assert.ok(feeDebit>0n);assert.ok(feeDebit<=sent.fee);assert.equal(receiverAfter-receiverBefore,amount);
 assert.equal(senderBefore-senderAfter,amount+(token.toLowerCase()===erc20Address.toLowerCase()?feeDebit:0n));
 assert.equal(await readEth(address,block),0n);assert.equal(await readEth(session.descriptor.ownerAddress,block),0n);
 const feeEvents=receipt.logs.filter(log=>log.address.toLowerCase()===erc20Address.toLowerCase()&&log.topics[0]===erc20.getEvent('Transfer').topicHash).map(log=>{const event=erc20.parseLog(log);return {from:event.args.from,to:event.args.to,amount:String(event.args.value)};});
 assert.equal(feeEvents.filter(event=>event.from===address&&event.to.toLowerCase()!==recipient.toLowerCase()).reduce((n,event)=>n+BigInt(event.amount),0n),feeDebit);
 if(reserve!==null){assert.ok(feeDebit<=reserve);assert.equal(senderAfter,reserve-feeDebit);}
 evidence.operations.push({scenario,token,amount:String(amount),senderBefore:String(senderBefore),senderAfter:String(senderAfter),recipientBefore:String(receiverBefore),recipientAfter:String(receiverAfter),feeTokenBefore:String(feeBefore),feeTokenAfter:String(feeAfter),quotedMaxFee:String(quote.fee),sdkMaxFee:String(sent.fee),actualFeeTokenDebit:String(feeDebit),reserve:reserve===null?null:String(reserve),walletEthAfter:'0',ownerEthAfter:'0',deployedBefore,userOperationHash:sent.hash,transactionHash:receipt.receipt.transactionHash,actualGasCostWei:String(BigInt(receipt.actualGasCost)),feeEvents,receipt});
 console.log('PASS',scenario,'actual token fee:',String(feeDebit));
}
try {
 await transfer(erc20Address,parseUnits('1',18),'first deployment; transferred asset also pays gas');
 await transfer(config.assets.USDC.contract,2000000n,'different stablecoin; separate fee-token debit');
 const balance=await readBalance(erc20Address,address);
 let reserve=(await session.quoteTransfer({token:erc20Address,recipient,amount:1n})).fee;
 let amount;
 for(let attempt=0;attempt<4;attempt++){
  amount=amountAfterFeeReserve(balance,reserve);
  const quote=await session.quoteTransfer({token:erc20Address,recipient,amount});
  if(quote.fee<=reserve)break;
  reserve=quote.fee;
  if(attempt===3)throw Error('Maximum fee reserve did not converge');
 }
 await transfer(erc20Address,amount,'maximum spendable after reserving token gas',reserve);
 evidence.passed=true;
 fs.writeFileSync(path.join(root,'docs/evidencia/ejecucion/f1/local-token-gas-node.json'),JSON.stringify(evidence,null,2)+'\n');
 console.log('PASS: token-paid gas, no user ETH, full recipient amount and explicit fee reserve');
} finally {session.dispose();provider.destroy();}
