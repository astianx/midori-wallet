import test from 'node:test';
import assert from 'node:assert/strict';
import { createCompatibilityApp,validateRpcRequest } from '../compat/app.js';

test('The F1 proxy rejects transaction submission, account requests and state overrides',()=>{
  for (const method of ['eth_sendRawTransaction','eth_sendTransaction','eth_sendUserOperation','personal_sign','debug_traceTransaction']) assert.throws(()=>validateRpcRequest({jsonrpc:'2.0',id:1,method,params:[]}));
  assert.throws(()=>validateRpcRequest({jsonrpc:'2.0',method:'eth_call',params:[{},'latest',{}]}));
  assert.throws(()=>validateRpcRequest({jsonrpc:'2.0',method:'eth_getLogs',params:[{address:'0x'+'1'.repeat(40),fromBlock:'0x0',toBlock:'0xffff'}]}));
});
test('API health works without Mongo, provider keys or wallet secrets',async()=>{
  const calls=[];const app=createCompatibilityApp({requestRpc:async(...args)=>{calls.push(args);return'0x14a34';}});
  const server=await new Promise(resolve=>{const s=app.listen(0,'127.0.0.1',()=>resolve(s));});
  try {
    const origin=`http://127.0.0.1:${server.address().port}`;
    const status=await fetch(origin+'/api/f1/status').then(r=>r.json());
    assert.equal(status.broadcastEnabled,false);assert.equal(calls.length,0);
    assert.equal((await fetch(origin+'/api/f1/rpc/base',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:1,method:'eth_sendRawTransaction',params:['0xdead']})})).status,403);
    assert.equal((await fetch(origin+'/api/f1/status',{headers:{Origin:'https://untrusted.example'}})).status,403);
    assert.equal(calls.length,0);
  } finally {await new Promise(resolve=>server.close(resolve));}
});

test('WDK constructor multicall is simulated with eth_call; it never submits a deployment', () => {
  assert.equal(validateRpcRequest({jsonrpc:'2.0',id:1,method:'eth_call',params:[{data:'0x60806040'},'latest']}).method,'eth_call');
  assert.throws(()=>validateRpcRequest({jsonrpc:'2.0',id:1,method:'eth_sendRawTransaction',params:['0x60806040']}));
});
