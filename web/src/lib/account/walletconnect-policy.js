import { BASE_ASSETS } from './AssetCatalog.js';

export const WALLETCONNECT_CHAIN = 'eip155:8453';
export const WALLETCONNECT_METHODS = ['personal_sign', 'eth_signTypedData', 'eth_signTypedData_v4', 'eth_sendTransaction', 'eth_accounts', 'eth_requestAccounts'];
export const walletConnectError = (message, code = 5000) => Object.assign(new Error(message), { code });
const sameAddress = (a, b) => typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();

export function validateWalletConnectRequest(event, session, address) {
  const { chainId, request, expiryTimestamp } = event.params || {};
  if (event.verifyContext?.verified?.isScam || event.verifyContext?.verified?.validation === 'INVALID') throw walletConnectError('This domain is flagged as malicious and potentially harmful.');
  if (expiryTimestamp && expiryTimestamp * 1000 <= Date.now()) throw walletConnectError('WalletConnect request expired', 8000);
  if (!session || event.topic !== session.topic || Date.now() >= session.expiry * 1000) throw walletConnectError('WalletConnect session expired', 7001);
  const namespace = session.namespaces.eip155;
  if (chainId !== WALLETCONNECT_CHAIN || !namespace?.accounts.some(account => account.toLowerCase() === `${chainId}:${address}`.toLowerCase())) throw walletConnectError('Only Base is supported', 5100);
  if (!WALLETCONNECT_METHODS.includes(request?.method) || !namespace.methods.includes(request.method)) throw walletConnectError('Not supported', 5101);
  if (JSON.stringify(request).length > 65536) throw walletConnectError('WalletConnect request is too large', -32602);
  if (['eth_accounts', 'eth_requestAccounts'].includes(request.method)) return { method: request.method };
  const params = request.params;
  if (!Array.isArray(params)) throw walletConnectError('Invalid WalletConnect request', -32602);
  if (request.method === 'eth_sendTransaction') return { method: request.method, transfer: decodeWalletConnectTransfer(params, address) };
  if (params.length !== 2 || !sameAddress(params[request.method === 'personal_sign' ? 1 : 0], address)) throw walletConnectError('The requested account does not match Midori', 5103);
  if (request.method === 'personal_sign') {
    if (typeof params[0] !== 'string' || !/^0x(?:[0-9a-fA-F]{2})*$/.test(params[0])) throw walletConnectError('Invalid message', -32602);
    return { method: request.method, data: params[0] };
  }
  let data;
  try { data = typeof params[1] === 'string' ? JSON.parse(params[1]) : params[1]; }
  catch { throw walletConnectError('Invalid typed data', -32602); }
  if (!data?.domain || !data.types || !data.message || !data.primaryType || !data.types[data.primaryType]) throw walletConnectError('Invalid typed data', -32602);
  if (data.domain.chainId != null && !/^(8453|0x2105)$/i.test(String(data.domain.chainId))) throw walletConnectError('Only Base is supported', 5100);
  return { method: request.method, data: structuredClone(data) };
}

export function decodeWalletConnectTransfer(params, address) {
  const tx = params[0];
  if (params.length !== 1 || !tx || !sameAddress(tx.from, address)) throw walletConnectError('The requested account does not match Midori', 5103);
  if (Object.keys(tx).some(key => !['from', 'to', 'data', 'value', 'gas', 'gasPrice', 'maxFeePerGas', 'maxPriorityFeePerGas', 'chainId', 'type'].includes(key))) throw walletConnectError('Not supported', 5101);
  if (tx.chainId != null && !/^(8453|0x2105)$/i.test(String(tx.chainId))) throw walletConnectError('Only Base is supported', 5100);
  if (tx.value != null && !/^(0|0x0+)$/i.test(String(tx.value))) throw walletConnectError('Only USDT and USDC transfers on Base are supported', 5101);
  const asset = BASE_ASSETS.find(asset => sameAddress(asset.contract, tx.to));
  if (!asset || typeof tx.data !== 'string' || !/^0xa9059cbb0{24}[0-9a-f]{40}[0-9a-f]{64}$/i.test(tx.data)) throw walletConnectError('Only USDT and USDC transfers on Base are supported', 5101);
  const recipient = `0x${tx.data.slice(34, 74)}`;
  const amountAtomic = BigInt(`0x${tx.data.slice(74)}`).toString();
  if (/^0x0{40}$/i.test(recipient) || sameAddress(recipient, address) || amountAtomic === '0') throw walletConnectError('Invalid transfer', -32602);
  return { ...asset, recipient, amountAtomic };
}
