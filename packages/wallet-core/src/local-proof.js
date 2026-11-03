import { createWalletSession, decodeTransferLog, reconcileTransferEvents } from './index.js';
import { PUBLIC_TEST_VECTOR } from './testing.js';

function loopback(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(url.hostname)) throw new Error('Local proof endpoints must use loopback');
  return value;
}
export async function runLocalSponsorship(config, { index = 10, runtime = 'node' } = {}) {
  if (config.chainId !== 31337) throw new Error('This proof only uses local Anvil chain 31337');
  for (const key of ['rpcUrl', 'bundlerUrl', 'paymasterUrl']) loopback(config[key]);
  async function rpc(url, method, params) {
    const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }), signal: AbortSignal.timeout(20000) });
    const body = await response.json();
    if (!response.ok || body.error) throw new Error(`Local ${method} failed: ${body.error?.message || response.status}`);
    return body.result;
  }
  if (BigInt(await rpc(config.rpcUrl, 'eth_chainId', [])) !== 31337n) throw new Error('Local chain mismatch');
  const balance = async (token, address) => BigInt(await rpc(config.rpcUrl, 'eth_call', [{ to: token, data: `0x70a08231${address.slice(2).toLowerCase().padStart(64, '0')}` }, 'latest']));
  const external = '0x1111111111111111111111111111111111111111';
  const options = { chainId: 31337, provider: config.rpcUrl, bundlerUrl: config.bundlerUrl, paymasterUrl: config.paymasterUrl };
  const recipientSession = await createWalletSession(PUBLIC_TEST_VECTOR.mnemonic, { ...options, index: index + 2 });
  const recipient = recipientSession.descriptor.address;recipientSession.dispose();
  const report = { testedAt: new Date().toISOString(), runtime, chainId: 31337, network: 'Local Anvil, not Base Sepolia or mainnet', tokens: 'Two ERC-20 mocks, not real USDT/USDC', operations: [], balances: [] };
  for (const [offset, symbol] of ['USDT', 'USDC'].entries()) {
    const session = await createWalletSession(PUBLIC_TEST_VECTOR.mnemonic, { ...options, index: index + offset });
    const address = session.descriptor.address;
    try {
      report.balances.push({ symbol, descriptor: session.descriptor, balances: await session.balances(config.assets) });
      const token = config.assets[symbol].contract;
      for (const [scenario, to, fixedAmount] of [['first deployment to new Safe', recipient, 1000000n], ['subsequent to external EOA', external, 2000000n], ['maximum to external EOA', external, null]]) {
        const before = await balance(token, address);const receivedBefore = await balance(token, to);
        const nativeBefore = BigInt(await rpc(config.rpcUrl, 'eth_getBalance', [address, 'latest']));
        const deployedBefore = await rpc(config.rpcUrl, 'eth_getCode', [address, 'latest']) !== '0x';
        const amount = fixedAmount ?? before;
        if (amount <= 0n || nativeBefore !== 0n) throw new Error('Expected funded mock token and zero user ETH');
        const sent = await session.submitSponsoredTransfer({ token, recipient: to, amount }, { allowLocalBroadcast: true });
        let receipt;
        for (let attempt = 0; attempt < 120; attempt++) {
          receipt = await rpc(config.bundlerUrl, 'eth_getUserOperationReceipt', [sent.hash]);
          if (receipt) break;
          await new Promise(resolve => setTimeout(resolve, 250));
        }
        if (!receipt?.success) throw new Error('Local sponsored operation failed or timed out');
        const after = await balance(token, address);const receivedAfter = await balance(token, to);
        const nativeAfter = BigInt(await rpc(config.rpcUrl, 'eth_getBalance', [address, 'latest']));
        if (before - after !== amount || receivedAfter - receivedBefore !== amount || nativeAfter !== 0n || sent.fee !== 0n) throw new Error('Sponsorship accounting failed');
        const events = receipt.logs.filter(log => log.address.toLowerCase() === token.toLowerCase()).map(log => decodeTransferLog(log, 31337));
        if (!events.some(event => event.from === address && event.to.toLowerCase() === to.toLowerCase() && BigInt(event.amount) === amount)) throw new Error('Transfer event missing');
        report.operations.push({ symbol, scenario, account: address, to, amount: String(amount), senderBefore: String(before), senderAfter: String(after), recipientBefore: String(receivedBefore), recipientAfter: String(receivedAfter), userEthBefore: String(nativeBefore), userEthAfter: String(nativeAfter), sdkFee: String(sent.fee), deployedBefore, deployedAfter: await rpc(config.rpcUrl, 'eth_getCode', [address, 'latest']) !== '0x', userOperationHash: sent.hash, transactionHash: receipt.receipt.transactionHash, actualGasCost: String(BigInt(receipt.actualGasCost)), actualGasUsed: String(BigInt(receipt.actualGasUsed)), paymaster: receipt.paymaster, events, receipt });
      }
    } finally { session.dispose(); }
  }
  const events = report.operations.flatMap(operation => operation.events);
  const replay = reconcileTransferEvents(events, events);
  const changed = reconcileTransferEvents(events, [], { canonicalBlockHashes: { [events[0].blockNumber]: '0x' + 'aa'.repeat(32) } });
  report.reconciliation = { count: events.length, deduplicatedCount: replay.length, duplicateReplayIgnored: replay.length === events.length, controlledReorgDetected: changed.find(event => event.id === events[0].id).canonical === false };
  report.passed = report.operations.length === 6 && report.reconciliation.duplicateReplayIgnored && report.reconciliation.controlledReorgDetected;
  return report;
}
