import fs from 'node:fs';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import path from 'node:path';
import {alto} from 'prool/instances';
import {paymaster} from '@pimlico/mock-paymaster';
import solc from 'solc';
import {JsonRpcProvider,ContractFactory,Wallet} from 'ethers';
const directory=fileURLToPath(new URL('.',import.meta.url));
const root=fileURLToPath(new URL('../../',import.meta.url));
const local=path.join(root,'.local/f1/local-chain');fs.mkdirSync(local,{recursive:true});
try { const response=await fetch('http://127.0.0.1:8545',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"jsonrpc":"2.0","id":1,"method":"eth_chainId","params":[]}',signal:AbortSignal.timeout(500)});if(response.ok)throw new Error('Port 8545 is already in use; stop the previous local experiment first'); } catch(error) { if(error.message.startsWith('Port '))throw error; }
const anvil=spawn(process.execPath,[path.join(directory,'node_modules/@foundry-rs/anvil/bin.mjs'),'--host','127.0.0.1','--port','8545','--chain-id','31337','--silent'],{cwd:directory,stdio:['ignore','ignore','inherit']});
const provider=new JsonRpcProvider('http://127.0.0.1:8545');provider.pollingInterval=100;
let ready=false;
for(let attempt=0;attempt<40;attempt++){try{if(BigInt(await provider.send('eth_chainId',[]))===31337n){ready=true;break;}}catch{}await new Promise(resolve=>setTimeout(resolve,250));}
if(!ready){anvil.kill('SIGTERM');throw new Error('Local Anvil did not start');}
const contracts=JSON.parse(fs.readFileSync(new URL('./fixtures/MainnetContracts.json',import.meta.url),'utf8'));
for(const [address,code] of Object.entries(contracts))await provider.send('anvil_setCode',[address,code]);
const key='0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80';
const bundler=alto({port:14337,entrypoints:['0x0000000071727De22E5E9d8BAf0edAc6f37da032'],rpcUrl:'http://127.0.0.1:8545',logLevel:'info','executor-private-keys':[key],'utility-private-key':[key],safeMode:false,pollingInterval:100,binary:path.join(directory,'node_modules/@pimlico/alto/esm/cli/alto.js')});
await bundler.start();
const origins=new Set(['http://127.0.0.1:8080','http://127.0.0.1:5173','http://127.0.0.1:4173','https://localhost']);
const bundlerMethods=new Set(['eth_chainId','eth_supportedEntryPoints','eth_estimateUserOperationGas','eth_sendUserOperation','eth_getUserOperationReceipt','eth_getUserOperationByHash','pimlico_getUserOperationGasPrice']);
const bundlerProxy=http.createServer(async(req,res)=>{
  const origin=req.headers.origin;
  if(origin&&!origins.has(origin)){res.writeHead(403);res.end();return;}
  if(origin){res.setHeader('Access-Control-Allow-Origin',origin);res.setHeader('Vary','Origin');}
  res.setHeader('Access-Control-Allow-Headers','Content-Type');res.setHeader('Access-Control-Allow-Methods','POST,OPTIONS');
  if(req.method==='OPTIONS'){res.writeHead(204);res.end();return;}
  if(req.method!=='POST'){res.writeHead(405);res.end();return;}
  try{
    const chunks=[];let size=0;
    for await(const chunk of req){size+=chunk.length;if(size>524288)throw Error('Request too large');chunks.push(chunk);}
    const body=Buffer.concat(chunks).toString();const request=JSON.parse(body);
    if(!bundlerMethods.has(request.method))throw Error('Unsupported local bundler method');
    const upstream=await fetch('http://127.0.0.1:14337',{method:'POST',headers:{'Content-Type':'application/json'},body,signal:AbortSignal.timeout(20000)});
    res.writeHead(upstream.status,{'Content-Type':'application/json'});res.end(await upstream.text());
  }catch(error){res.writeHead(400,{'Content-Type':'application/json'});res.end(JSON.stringify({error:{message:error.message}}));}
});
await new Promise(resolve=>bundlerProxy.listen(4337,'127.0.0.1',resolve));console.log('Local bundler ready, with loopback CORS bridge');
const sponsor=paymaster({port:3000,host:'127.0.0.1',anvilRpc:'http://127.0.0.1:8545',altoRpc:'http://127.0.0.1:4337'});await sponsor.start();console.log('Local sponsor ready');
const source=`pragma solidity ^0.8.20; contract MockStablecoin {string public name; string public symbol; uint8 public constant decimals=6; mapping(address=>uint256) public balanceOf; event Transfer(address indexed from,address indexed to,uint256 value); constructor(string memory s){name=s;symbol=s;} function mint(address to,uint256 amount) external {balanceOf[to]+=amount;emit Transfer(address(0),to,amount);} function transfer(address to,uint256 amount) external returns(bool){require(balanceOf[msg.sender]>=amount,"balance");balanceOf[msg.sender]-=amount;balanceOf[to]+=amount;emit Transfer(msg.sender,to,amount);return true;} }`;
const input={language:'Solidity',sources:{'Mock.sol':{content:source}},settings:{evmVersion:'cancun',outputSelection:{'*':{'*':['abi','evm.bytecode.object']}}}};
const output=JSON.parse(solc.compile(JSON.stringify(input)));if(output.errors?.some(e=>e.severity==='error'))throw Error(JSON.stringify(output.errors));
const artifact=output.contracts['Mock.sol'].MockStablecoin;const wallet=new Wallet(key,provider);const factory=new ContractFactory(artifact.abi,artifact.evm.bytecode.object,wallet);
let nonce=await provider.getTransactionCount(wallet.address);const assets={};
for(const symbol of ['USDT','USDC']){const token=await factory.deploy('Mock'+symbol,{nonce:nonce++});await token.waitForDeployment();assets[symbol]={contract:await token.getAddress(),decimals:6,representation:'local mock; no real stablecoin'};}
fs.writeFileSync(path.join(local,'state.json'),JSON.stringify({chainId:31337,rpcUrl:'http://127.0.0.1:8545',bundlerUrl:'http://127.0.0.1:4337?pimlico',paymasterUrl:'http://127.0.0.1:3000?pimlico',assets,abi:artifact.abi},null,2));
console.log('Local tokens ready',assets);
let stopping=false;async function stop(){if(stopping)return;stopping=true;await sponsor.stop();await new Promise(resolve=>bundlerProxy.close(resolve));await bundler.stop();anvil.kill('SIGTERM');provider.destroy();process.exit();}
process.once('SIGINT',stop);process.once('SIGTERM',stop);await new Promise(()=>{});
