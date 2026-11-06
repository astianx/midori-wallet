import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import { chromium, _electron as electron } from 'playwright-core';
import { JsonRpcProvider,Contract,Wallet } from 'ethers';
import { createWalletSession } from '../../packages/wallet-core/src/index.js';
import { PUBLIC_TEST_VECTOR } from '../../packages/wallet-core/src/testing.js';
import { runLocalSponsorship } from '../../packages/wallet-core/src/local-proof.js';
const root=fileURLToPath(new URL('../../',import.meta.url));const config=JSON.parse(fs.readFileSync(path.join(root,'.local/f1/local-chain/state.json')));
const provider=new JsonRpcProvider(config.rpcUrl);provider.pollingInterval=100;const funded=new Wallet('0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80',provider);
const target=process.argv[2]||'node';const index={node:10,chromium:20,electron:30,android:40}[target];if(!index)throw Error('Unknown runtime');
let nonce=await provider.getTransactionCount(funded.address);const deposits=[];
for(let offset=0;offset<2;offset++){
 const session=await createWalletSession(PUBLIC_TEST_VECTOR.mnemonic,{chainId:31337,index:index+offset});const address=session.descriptor.address;session.dispose();
 if(await provider.getCode(address)!=='0x')throw Error('Use a clean local chain or unused account indexes for a first-deployment proof');
 for(const [symbol,asset] of Object.entries(config.assets)){const token=new Contract(asset.contract,config.abi,funded);const tx=await token.mint(address,100000000n,{nonce:nonce++});const receipt=await tx.wait();deposits.push({symbol,to:address,transactionHash:tx.hash,logs:receipt.logs.map(log=>log.toJSON())});}
}
let report;
if(target==='node')report=await runLocalSponsorship(config,{index,runtime:'node'});
else if(target==='chromium'){
 const browser=await chromium.launch({executablePath:process.env.MIDORI_CHROMIUM_EXECUTABLE || path.join(os.homedir(),'.cache/ms-playwright/chromium-1234/chrome-linux64/chrome'),headless:true,chromiumSandbox:true});try{const page=await browser.newPage();await page.goto('http://127.0.0.1:4173');await page.waitForFunction(()=>!!window.midoriF1?.runLocalProof);report=await page.evaluate(c=>window.midoriF1.runLocalProof(c),{...config,index});}finally{await browser.close();}
}else if(target==='electron'){
 const app=await electron.launch({executablePath:root+'/electron/node_modules/electron/dist/electron',args:[root+'/electron/f1-main.js'],cwd:root+'/electron',env:{...process.env,MIDORI_F1_PROFILE:`sponsor-${process.pid}`,MIDORI_WEB_URL:'http://127.0.0.1:4173'},chromiumSandbox:true});try{const page=await app.firstWindow();await page.waitForFunction(()=>!!window.midoriF1?.runLocalProof);report=await page.evaluate(c=>window.midoriF1.runLocalProof(c),{...config,index});}finally{await app.close();}
}else{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9223',{noDefaults:true});try{const page=browser.contexts()[0].pages().find(p=>p.url().startsWith('https://localhost/'));await page.reload();await page.waitForFunction(()=>!!window.midoriF1?.runLocalProof);report=await page.evaluate(c=>window.midoriF1.runLocalProof(c),{...config,index});}finally{await browser.close();}
}
fs.writeFileSync(path.join(root,`docs/evidencia/ejecucion/f1/local-sponsorship-${target}.json`),JSON.stringify({...report,deposits},null,2)+'\n');console.log('PASS',target,report.operations.length,'sponsored operations');provider.destroy();
