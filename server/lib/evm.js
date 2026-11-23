import { getAddress, Interface } from 'ethers';
import { evmNetworks, stablecoinNetwork, networkAsset } from '../../config/stablecoin-networks.js';
import { evmToken, evmRecipient, evmAccount, validateEvmPayment, inspectEvmTransfer, evmSame } from '../../packages/wallet-core/src/evm.js';
import { paymentOperation } from '../../packages/wallet-core/src/payment.js';
import { SAFE_CONFIG } from '../../packages/wallet-core/src/safe-config.js';
import { userOperationEvents } from './payment-provider.js';
import { ApiError, exactKeys, requireValue } from './errors.js';
const entryPoint = new Interface(['function getNonce(address,uint192) view returns(uint256)', 'function balanceOf(address) view returns(uint256)']);
const quantity = value => `0x${BigInt(value).toString(16)}`;
const hashPattern = /^0x[0-9a-f]{64}$/i;
export function evmGateway({ fetchImpl = fetch, rpcUrls = {}, bundlerUrls = {} } = {}) {
  function network(id) {
    const found = stablecoinNetwork(id);
    requireValue(evmNetworks.includes(found), 'Unsupported network');
    return found;
  }
  async function request(url, method, params) {
    try {
      if (new URL(url).protocol !== 'https:') throw new Error();
      const response = await fetchImpl(url, { redirect: 'error', method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }), signal: AbortSignal.timeout(20000) });
      const body = await response.json();
      if (!response.ok || body.error || !Object.hasOwn(body, 'result')) throw new Error();
      return body.result;
    } catch { throw new ApiError(502, 'CHAIN_UNAVAILABLE', 'Network is temporarily unavailable'); }
  }
  const rpc = (chain, method, params = []) => request(rpcUrls[chain.id] || chain.rpcUrl, method, params);
  const bundler = (chain, method, params = []) => request(bundlerUrls[chain.id] || chain.bundlerUrl, method, params);
  async function connect(id, operations = false) {
    const chain = network(id);
    if (BigInt(await rpc(chain, 'eth_chainId')) !== BigInt(chain.chainId)) throw new ApiError(502, 'PAYMENT_NETWORK_MISMATCH', 'Unexpected RPC network');
    if (operations) {
      const [chainId, entries] = await Promise.all([bundler(chain, 'eth_chainId'), bundler(chain, 'eth_supportedEntryPoints')]);
      if (BigInt(chainId) !== BigInt(chain.chainId) || !Array.isArray(entries) || !entries.some(value => evmSame(value, SAFE_CONFIG.entryPoint))) throw new ApiError(502, 'PAYMENT_NETWORK_MISMATCH', 'Unexpected bundler network');
    }
    return chain;
  }
  async function balances(chain, owner, asset) {
    const block = await rpc(chain, 'eth_blockNumber');
    const [value, native] = await Promise.all([
      rpc(chain, 'eth_call', [{ to: asset.contract, data: evmToken.encodeFunctionData('balanceOf', [owner]) }, block]),
      rpc(chain, 'eth_getBalance', [owner, block]),
    ]);
    return { balanceAtomic: BigInt(value).toString(), nativeBalanceAtomic: BigInt(native).toString(), blockNumber: BigInt(block).toString() };
  }
  return {
    async balance(id, owner, contract) {
      const chain = await connect(id), asset = networkAsset(chain, contract);
      requireValue(asset, 'Unsupported token');
      return { network: id, chainId: chain.chainId, contract: asset.contract, address: getAddress(owner), readAt: new Date().toISOString(), ...await balances(chain, owner, asset) };
    },
    async quote(id, descriptor, body) {
      exactKeys(body, ['contract', 'to', 'amountAtomic']);
      const chain = await connect(id, true), asset = networkAsset(chain, body.contract), smart = evmAccount(descriptor), owner = descriptor.address;
      requireValue(asset && typeof body.amountAtomic === 'string' && /^[1-9][0-9]{0,77}$/.test(body.amountAtomic) && BigInt(body.amountAtomic) < 2n ** 256n);
      let to;
      try { to = evmRecipient(body.to, owner, asset.contract); } catch { throw new ApiError(400, 'INVALID_RECIPIENT', 'Invalid recipient'); }
      const balance = await balances(chain, owner, asset);
      if (BigInt(balance.balanceAtomic) < BigInt(body.amountAtomic)) throw new ApiError(400, 'INSUFFICIENT_FUNDS', 'Insufficient token balance');
      const [nonce, price, code, deposit] = await Promise.all([
        rpc(chain, 'eth_call', [{ to: descriptor.entryPoint, data: entryPoint.encodeFunctionData('getNonce', [owner, 0n]) }, 'latest']),
        rpc(chain, 'eth_gasPrice'), rpc(chain, 'eth_getCode', [owner, 'latest']),
        rpc(chain, 'eth_call', [{ to: descriptor.entryPoint, data: entryPoint.encodeFunctionData('balanceOf', [owner]) }, 'latest']),
      ]);
      if (BigInt(balance.nativeBalanceAtomic) + BigInt(deposit) === 0n) throw new ApiError(400, 'INSUFFICIENT_GAS_FUNDS', 'Insufficient native coin balance');
      const gasPrice = (BigInt(price) * 125n + 99n) / 100n;
      const operation = await smart.createUserOperation([{ to: asset.contract, value: 0n, data: evmToken.encodeFunctionData('transfer', [to, BigInt(body.amountAtomic)]) }], null, null, { nonce: BigInt(nonce), maxFeePerGas: gasPrice, maxPriorityFeePerGas: gasPrice, skipGasEstimation: true });
      operation.factory = code === '0x' ? descriptor.factoryAddress : null;
      operation.factoryData = code === '0x' ? descriptor.factoryData : null;
      const estimate = await bundler(chain, 'eth_estimateUserOperationGas', [paymentOperation({ ...operation, callGasLimit: 0n, verificationGasLimit: 0n, preVerificationGas: 0n, maxFeePerGas: 0n, maxPriorityFeePerGas: 0n }, true), descriptor.entryPoint]);
      for (const field of ['callGasLimit', 'verificationGasLimit', 'preVerificationGas']) operation[field] = (BigInt(estimate[field]) * 120n + 99n) / 100n + (field === 'verificationGasLimit' ? 55000n : 0n);
      operation.signature = '0x';
      const maxFee = (operation.callGasLimit + operation.verificationGasLimit + operation.preVerificationGas) * gasPrice;
      const quote = { network: id, chainId: chain.chainId, from: getAddress(owner), to, contract: asset.contract, symbol: asset.symbol, decimals: asset.decimals, amountAtomic: body.amountAtomic, nonce: String(operation.nonce), operation: paymentOperation(operation, true), maxFeeAtomic: String(maxFee), nativeSymbol: chain.nativeSymbol, expiresAt: new Date(Date.now() + 120000).toISOString() };
      validateEvmPayment(quote, descriptor, { ...asset, chainId: chain.chainId });
      if (BigInt(balance.nativeBalanceAtomic) + BigInt(deposit) < maxFee) throw new ApiError(400, 'INSUFFICIENT_GAS_FUNDS', 'Insufficient native coin balance');
      return quote;
    },
    async broadcast(id, descriptor, body) {
      exactKeys(body, ['raw']);
      const chain = await connect(id, true);
      let parsed;
      try { parsed = inspectEvmTransfer(body.raw, chain, descriptor); } catch { throw new ApiError(400, 'INVALID_REQUEST', 'Invalid signed transfer'); }
      const receipt = await bundler(chain, 'eth_getUserOperationReceipt', [parsed.hash]);
      const known = receipt || await bundler(chain, 'eth_getUserOperationByHash', [parsed.hash]);
      if (known) return { hash: parsed.hash, status: 'submitted', ...(receipt?.receipt?.transactionHash ? { transactionHash: receipt.receipt.transactionHash } : {}) };
      if (Date.parse(parsed.quote.expiresAt) <= Date.now()) throw new ApiError(409, 'QUOTE_EXPIRED', 'Payment quote expired');
      const nonce = BigInt(await rpc(chain, 'eth_call', [{ to: descriptor.entryPoint, data: entryPoint.encodeFunctionData('getNonce', [descriptor.address, 0n]) }, 'latest']));
      if (nonce !== parsed.operation.nonce) throw new ApiError(409, 'PAYMENT_TRACKING_REQUIRED', 'Account nonce changed; check the payment');
      const result = await bundler(chain, 'eth_sendUserOperation', [paymentOperation(parsed.operation, true), descriptor.entryPoint]);
      if (!evmSame(result, parsed.hash)) throw new ApiError(502, 'NETWORK_UNCONFIRMED', 'Unconfirmed broadcast');
      return { hash: parsed.hash, status: 'submitted' };
    },
    async status(id, descriptor, hash, payment) {
      requireValue(hashPattern.test(hash));
      const chain = await connect(id, true), result = await bundler(chain, 'eth_getUserOperationReceipt', [hash]);
      if (!result) return { hash, status: 'ambiguous' };
      requireValue(evmSame(result.userOpHash, hash) && evmSame(result.sender, descriptor.address) && hashPattern.test(result.receipt?.transactionHash), 'Unexpected operation receipt');
      const transactionHash = result.receipt.transactionHash, receipt = await rpc(chain, 'eth_getTransactionReceipt', [transactionHash]);
      if (!receipt) return { hash, status: 'pending', transactionHash };
      const [latest, canonical] = await Promise.all([rpc(chain, 'eth_blockNumber'), rpc(chain, 'eth_getBlockByNumber', [receipt.blockNumber, false])]);
      if (!canonical || !evmSame(canonical.hash, receipt.blockHash)) return { hash, status: 'pending', transactionHash };
      let event, start = 0, stop = -1;
      for (let index = 0; index < receipt.logs.length; index++) {
        const log = receipt.logs[index];
        if (!evmSame(log.address, descriptor.entryPoint) || log.removed) continue;
        try {
          const decoded = userOperationEvents.parseLog(log);
          if (decoded?.name === 'BeforeExecution') start = index + 1;
          if (decoded?.name === 'UserOperationEvent') {
            if (evmSame(decoded.args.userOpHash, hash)) { event = decoded; stop = index; break; }
            start = index + 1;
          }
        } catch { /* Only canonical EntryPoint events identify this operation. */ }
      }
      requireValue(event && evmSame(event.args.sender, descriptor.address) && String(event.args.nonce) === payment.nonce, 'Operation evidence is missing');
      const transferred = receipt.logs.slice(start, stop).some(log => {
        if (!evmSame(log.address, payment.contract) || log.removed) return false;
        try { const decoded = evmToken.parseLog(log); return evmSame(decoded.args.from, descriptor.address) && evmSame(decoded.args.to, payment.to) && String(decoded.args.value) === payment.amountAtomic; } catch { return false; }
      });
      const confirmations = BigInt(latest) - BigInt(receipt.blockNumber) + 1n;
      if (event.args.success) requireValue(transferred, 'Transfer evidence is missing');
      return { hash, transactionHash, status: confirmations < 12n ? 'pending' : BigInt(receipt.status) === 1n && event.args.success && transferred ? 'confirmed' : 'reverted', confirmations: String(confirmations) };
    },
    async transaction(id, owner, contract, reference) {
      const chain = await connect(id), asset = networkAsset(chain, contract);
      requireValue(asset && /^0x[0-9a-f]{64}:0x[0-9a-f]+$/i.test(reference));
      const [hash,index] = reference.split(':');
      const receipt = await rpc(chain, 'eth_getTransactionReceipt', [hash]);
      const log = receipt?.logs.find(log => evmSame(log.address,asset.contract) && log.logIndex.toLowerCase() === index.toLowerCase());
      requireValue(log && !log.removed && evmSame(log.transactionHash,hash));
      const decoded = evmToken.parseLog(log);
      requireValue(evmSame(decoded.args.from,owner) || evmSame(decoded.args.to,owner));
      const block = await rpc(chain, 'eth_getBlockByNumber', [receipt.blockNumber,false]);
      requireValue(block && evmSame(block.hash,receipt.blockHash));
      const latest = BigInt(await rpc(chain, 'eth_blockNumber'));
      return { id: reference, transactionHash: hash, contract: asset.contract, from: decoded.args.from, to: decoded.args.to, amountAtomic: String(decoded.args.value), timestamp: new Date(Number(BigInt(block.timestamp))*1000).toISOString(), status: latest-BigInt(receipt.blockNumber)+1n >= 12n ? 'confirmed' : 'pending', incoming: evmSame(decoded.args.to,owner) };
    },
    async history(id, owner, contract, cursor) {
      const chain = await connect(id), asset = networkAsset(chain, contract);
      requireValue(asset && (cursor === undefined || /^(0|[1-9][0-9]{0,14})$/.test(cursor)));
      const latest = BigInt(await rpc(chain, 'eth_blockNumber')), end = cursor === undefined ? latest : BigInt(cursor);
      requireValue(end <= latest);
      const start = end > 1999n ? end - 1999n : 0n, topic = `0x${owner.slice(2).toLowerCase().padStart(64, '0')}`;
      const base = { address: asset.contract, fromBlock: quantity(start), toBlock: quantity(end) };
      const logs = (await Promise.all([[evmToken.getEvent('Transfer').topicHash, topic], [evmToken.getEvent('Transfer').topicHash, null, topic]].map(topics => rpc(chain, 'eth_getLogs', [{ ...base, topics }])))).flat();
      requireValue(logs.length <= 300 && logs.every(log => hashPattern.test(log.transactionHash)));
      const unique = [...new Map(logs.map(log => [`${log.transactionHash}:${log.logIndex}`, log])).values()];
      const transactions = await Promise.all(unique.filter(log => !log.removed).map(async log => {
        requireValue(evmSame(log.address, asset.contract));
        const decoded = evmToken.parseLog(log), block = await rpc(chain, 'eth_getBlockByNumber', [log.blockNumber, false]);
        requireValue(block && evmSame(block.hash, log.blockHash) && (evmSame(decoded.args.from, owner) || evmSame(decoded.args.to, owner)));
        return { id: `${log.transactionHash}:${log.logIndex}`, transactionHash: log.transactionHash, contract: asset.contract, from: decoded.args.from, to: decoded.args.to, amountAtomic: String(decoded.args.value), timestamp: new Date(Number(BigInt(block.timestamp)) * 1000).toISOString(), status: latest - BigInt(log.blockNumber) + 1n >= 12n ? 'confirmed' : 'pending', incoming: evmSame(decoded.args.to, owner), blockNumber: Number(BigInt(log.blockNumber)) };
      }));
      return { transactions: transactions.sort((a,b) => b.blockNumber-a.blockNumber), cursor: start ? String(start-1n) : undefined, hasMore: start > 0n, indexing: { enabled: true, status: start > 0n ? 'backfilling' : 'caught_up' } };
    },
  };
}
