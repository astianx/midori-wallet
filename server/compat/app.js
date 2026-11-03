import express from 'express';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { AbiCoder, getAddress, isAddress } from 'ethers';

const ROOT = fileURLToPath(new URL('../../', import.meta.url));
const NETWORKS = JSON.parse(fs.readFileSync(path.join(ROOT, 'config/networks.json'), 'utf8'));
const ALLOWED_METHODS = new Set(['eth_chainId', 'eth_blockNumber', 'eth_getCode', 'eth_getBalance', 'eth_call', 'eth_getLogs', 'eth_getTransactionReceipt', 'eth_getBlockByNumber']);
const abi = AbiCoder.defaultAbiCoder();

export function validateRpcRequest(body) {
  if (!body || body.jsonrpc !== '2.0' || !ALLOWED_METHODS.has(body.method) || !Array.isArray(body.params)) throw new Error('Only the approved read-only RPC methods are available');
  if (body.params.length > 2) throw new Error('State overrides and extra parameters are forbidden');
  if (body.method === 'eth_call') {
    const tx = body.params[0];
    if (!tx || (tx.to != null && !isAddress(tx.to)) || !/^0x[0-9a-fA-F]*$/.test(tx.data || '') || tx.data.length > 32768) throw new Error('Invalid read-only contract call');
    if (Object.keys(tx).some(key => !['to','data'].includes(key))) throw new Error('Only target and data are accepted in a contract call');
  }
  if (body.method === 'eth_getLogs') {
    const filter = body.params[0];
    if (!filter || !filter.address || !/^0x[0-9a-fA-F]+$/.test(filter.fromBlock || '') || !/^0x[0-9a-fA-F]+$/.test(filter.toBlock || '')) throw new Error('Log queries require explicit contract and block range');
    if (BigInt(filter.toBlock) < BigInt(filter.fromBlock) || BigInt(filter.toBlock) - BigInt(filter.fromBlock) > 2000n) throw new Error('Log range exceeds 2000 blocks');
  }
  return { jsonrpc: '2.0', id: typeof body.id === 'number' || typeof body.id === 'string' ? body.id : 1, method: body.method, params: body.params };
}

export function createCompatibilityApp({ requestRpc, networks = NETWORKS, webDirectory = path.join(ROOT, 'server/dist'), allowedOrigins = [], persistence = 'Not implemented; PostgreSQL scheduled in F2' } = {}) {
  const app = express();
  const origins = new Set(allowedOrigins);
  const started = new Date().toISOString();
  app.disable('x-powered-by');
  app.use((req, res, next) => {
    const origin = req.get('origin');
    if (origin && !origins.has(origin)) return res.status(403).json({ error: 'Origin not allowed' });
    if (origin) { res.setHeader('Access-Control-Allow-Origin', origin);res.setHeader('Vary','Origin'); }
    res.setHeader('Access-Control-Allow-Headers','Content-Type');
    res.setHeader('Access-Control-Allow-Methods','GET,POST,OPTIONS');
    res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');
    if (req.method === 'OPTIONS') return res.sendStatus(204);
    next();
  });
  app.use(express.json({ limit: '40kb', strict: true }));
  const rpc = requestRpc || (async (network, method, params) => {
    const url = networks[network]?.rpcUrl;
    if (!url) throw new Error('Unknown network');
    const response = await fetch(url, { redirect: 'error', method: 'POST', headers: { 'Content-Type':'application/json' }, body: JSON.stringify({ jsonrpc:'2.0',id:1,method,params }), signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw new Error(`Upstream HTTP ${response.status}`);
    const body = await response.json();
    if (body.error || !Object.hasOwn(body,'result')) throw new Error('RPC read failed');
    return body.result;
  });
  app.get('/healthz', (req,res) => res.json({ status:'ok', phase:'F1' }));
  app.get('/api/f1/status', (req,res) => res.json({ status:'ok', phase:'F1', startedAt:started, persistence, signing:'client-side only', broadcastEnabled:false, integrations:{ legacyProviders:false, docker:false }, nativeProofs:{ios:'pending macOS',safari:'pending macOS'} }));
  app.get('/api/f1/networks', (req,res) => res.json(Object.fromEntries(Object.entries(networks).map(([name,network])=>[name,{ chainId:network.chainId,testnet:network.testnet,assets:network.assets }]))));
  app.post('/api/f1/rpc/:network', async (req,res) => {
    if (!Object.hasOwn(networks,req.params.network)) return res.status(404).json({error:'Unknown network'});
    let body;
    try { body=validateRpcRequest(req.body); } catch (error) { return res.status(403).json({error:error.message}); }
    try {
      const result=await rpc(req.params.network,body.method,body.params);
      res.json({ jsonrpc:'2.0',id:body.id,result });
    } catch { res.status(502).json({jsonrpc:'2.0',id:body.id,error:{code:-32000,message:'Upstream read unavailable'}}); }
  });
  const cache = new Map();
  app.get('/api/f1/assets/:network', async (req,res) => {
    const network=Object.hasOwn(networks,req.params.network) ? networks[req.params.network] : null;
    if (!network || !isAddress(req.query.account || '')) return res.status(400).json({error:'Valid network and account required'});
    const account=getAddress(req.query.account);const key=`${req.params.network}:${account}`;const saved=cache.get(key);
    if (saved && Date.now()-saved.created < 15000) return res.json(saved.data);
    try {
      const chain=await rpc(req.params.network,'eth_chainId',[]);
      if (BigInt(chain)!==BigInt(network.chainId)) return res.status(502).json({error:'RPC chain mismatch'});
      const block=await rpc(req.params.network,'eth_blockNumber',[]);
      const assets={};
      for (const [symbol,asset] of Object.entries(network.assets)) {
        if (!asset.contract) { assets[symbol]={...asset,status:'unconfigured',balanceAtomic:null};continue; }
        const [code,decimalsHex,symbolHex,balanceHex]=await Promise.all([
          rpc(req.params.network,'eth_getCode',[asset.contract,block]),
          rpc(req.params.network,'eth_call',[{to:asset.contract,data:'0x313ce567'},block]),
          rpc(req.params.network,'eth_call',[{to:asset.contract,data:'0x95d89b41'},block]),
          rpc(req.params.network,'eth_call',[{to:asset.contract,data:`0x70a08231${account.slice(2).toLowerCase().padStart(64,'0')}`},block]),
        ]);
        const decimals=Number(BigInt(decimalsHex));const observedSymbol=abi.decode(['string'],symbolHex)[0];
        if (code==='0x' || decimals!==asset.decimals || observedSymbol!==symbol) throw new Error('Token metadata mismatch');
        assets[symbol]={...asset,status:'verified',observedSymbol,decimals,account,balanceAtomic:BigInt(balanceHex).toString(),codeSha256:createHash('sha256').update(code).digest('hex')};
      }
      const data={ network:req.params.network,chainId:network.chainId,account,blockNumber:BigInt(block).toString(),readAt:new Date().toISOString(),assets };
      cache.set(key,{created:Date.now(),data});res.json(data);
    } catch {res.status(502).json({error:'Could not verify balances and contracts on the selected network; no cached value is presented as fresh'});}
  });
  app.use('/api',(req,res)=>res.status(404).json({error:'Only F1 read-only endpoints are implemented'}));
  app.get('/LICENSE',(req,res)=>res.sendFile(path.join(ROOT,'LICENSE')));
  app.use(express.static(webDirectory,{dotfiles:'deny',index:'index.html'}));
  app.use((req,res)=>res.status(404).json({error:'Build the shared web application first'}));
  app.use((error,req,res,next)=>res.status(error.status || 500).json({error:'Invalid request'}));
  return app;
}
