import { validatePaymentOperation, paymentSigningData } from './payment.js';
import WDK from '@tetherto/wdk';
import WalletManagerEvm from '@tetherto/wdk-wallet-evm';
import WalletManagerEvmErc4337, { WalletAccountReadOnlyEvmErc4337 } from '@tetherto/wdk-wallet-evm-erc-4337';
import { HDNodeWallet, Interface, getAddress, verifyMessage, verifyTypedData } from 'ethers';
import { ENTRYPOINT_V7, SafeAccountV0_3_0 } from 'abstractionkit';

export const SDK_VERSIONS = Object.freeze({ core: '1.0.0-beta.22', evm: '1.0.0-beta.19', erc4337: '1.0.0-beta.22', abstractionkit: '0.4.0', ethers: '6.17.0' });
export const SAFE_CONFIG = Object.freeze({
  safeModulesVersion: '0.3.0', entryPoint: ENTRYPOINT_V7,
  saltNonce: '0x69b348339eea4ed93f9d11931c3b894c8f9d8c7663a053024b11cb7eb4e5a1f6',
  module: '0x75cf11467937ce3F2f357CE24ffc3DBF8fD5c226', setup: '0x2dd68b007B46fBe91B9A7c3EDa5A7a1063cB5b47',
});
const tokenInterface = new Interface(['event Transfer(address indexed from,address indexed to,uint256 value)', 'function balanceOf(address) view returns (uint256)']);

export function parseTokenAmount(value, decimals = 6) {
  if (typeof value !== 'string' || !/^(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(value)) throw new Error('Invalid decimal amount');
  const [whole, fraction = ''] = value.split('.');
  if (!Number.isInteger(decimals) || decimals < 0 || decimals > 36 || fraction.length > decimals) throw new Error('Invalid token precision');
  const amount = BigInt(whole) * 10n ** BigInt(decimals) + BigInt(fraction.padEnd(decimals, '0') || '0');
  if (amount >= 2n ** 256n) throw new Error('Amount exceeds uint256');
  return amount;
}

export function formatTokenAmount(amount, decimals = 6) {
  const units = BigInt(amount);
  if (units < 0n) throw new Error('Negative token amount');
  const scale = 10n ** BigInt(decimals);
  const fraction = (units % scale).toString().padStart(decimals, '0').replace(/0+$/, '');
  return `${units / scale}${fraction ? `.${fraction}` : ''}`;
}

export function noNetworkProvider(onRequest = () => {}) {
  return { async request(request) { onRequest(request); throw new Error('Network access is forbidden in this offline proof'); } };
}

export function gasPaymentConfig({ feeMode = 'sponsored', paymasterAddress, paymasterToken, sponsorshipPolicyId, transferMaxFee } = {}) {
  if (feeMode === 'sponsored') {
    if (paymasterAddress || paymasterToken) throw new Error('Sponsored mode cannot charge a paymaster token');
    return { isSponsored: true, ...(sponsorshipPolicyId ? { sponsorshipPolicyId } : {}) };
  }
  if (feeMode !== 'token') throw new Error('Choose sponsored or token fee mode');
  if (sponsorshipPolicyId) throw new Error('A sponsorship policy does not configure token-paid gas');
  if (!paymasterAddress || !paymasterToken) throw new Error('Token-paid gas requires a verified paymaster and fee-token contract');
  if (transferMaxFee == null || BigInt(transferMaxFee) <= 0n) throw new Error('Token-paid gas requires a positive fee cap in fee-token base units');
  return { isSponsored: false, useNativeCoins: false, paymasterAddress: getAddress(paymasterAddress), paymasterToken: { address: getAddress(typeof paymasterToken === 'string' ? paymasterToken : paymasterToken.address) }, transferMaxFee: BigInt(transferMaxFee) };
}

export function amountAfterFeeReserve(balance, feeReserve) {
  const available = BigInt(balance);const reserve = BigInt(feeReserve);
  if (available < 0n || reserve < 0n) throw new Error('Balances and fee reserve cannot be negative');
  return available > reserve ? available - reserve : 0n;
}

export async function createWalletSession(seed, { chainId = 84532, index = 0, provider = noNetworkProvider(), bundlerUrl, paymasterUrl, sponsorshipPolicyId, feeMode = 'sponsored', paymasterAddress, paymasterToken, transferMaxFee } = {}) {
  if (!Number.isSafeInteger(chainId) || chainId < 1 || !Number.isSafeInteger(index) || index < 0) throw new Error('Invalid chain or account index');
  const endpoint = `https://api.candide.dev/public/v3/${chainId}`;
  const gas = gasPaymentConfig({ feeMode, paymasterAddress, paymasterToken, sponsorshipPolicyId, transferMaxFee });
  const config = { chainId, provider, safeModulesVersion: SAFE_CONFIG.safeModulesVersion, bundlerUrl: bundlerUrl || endpoint, paymasterUrl: paymasterUrl || endpoint, ...gas };
  const wdk = new WDK(seed).registerWallet('base', WalletManagerEvmErc4337, config);
  const ownerManager = new WalletManagerEvm(seed, {});
  let disposed = false;
  try {
    const account = await wdk.getAccount('base', index);
    const owner = await ownerManager.getAccount(index);
    const ownerAddress = getAddress(await owner.getAddress());
    const address = getAddress(await account.getAddress());
    const [factoryAddress, factoryData] = SafeAccountV0_3_0.createFactoryAddressAndData([ownerAddress], { c2Nonce: BigInt(SAFE_CONFIG.saltNonce), entrypointAddress: SAFE_CONFIG.entryPoint, safe4337ModuleAddress: SAFE_CONFIG.module, safeModuleSetupAddress: SAFE_CONFIG.setup });
    const descriptor = Object.freeze({
      schema: 'midori.safe-account', version: 1, chainId, accountType: 'safe-erc4337', index,
      derivationPath: account.path, ownerAddress, address, factoryAddress, factoryData, threshold: 1, ...SAFE_CONFIG, sdk: SDK_VERSIONS,
    });
    const ready = () => { if (disposed) throw new Error('Wallet session is locked'); };
    return {
      descriptor,
      feeMode,
      feeToken: gas.paymasterToken?.address || null,
      receiveAddress(symbol) { ready(); if (!['USDT', 'USDC'].includes(symbol)) throw new Error('Unsupported stablecoin'); return address; },
      async signMessage(message) { ready(); return account.sign(message); },
      async signTypedData(data) { ready(); return account.signTypedData(data); },
      async signPayment(operation, expiresAt, payment) {
        ready(); if (Date.parse(expiresAt) <= Date.now()) throw new Error('Payment quote expired');
        validatePaymentOperation(operation, descriptor, payment);
        const typed = paymentSigningData(operation, descriptor, expiresAt);
        return account.signTypedData({ domain: typed.domain, types: typed.types, message: typed.messageValue });
      },
      verifyOwnerMessage(message, signature) { return getAddress(verifyMessage(message, signature)) === ownerAddress; },
      verifyOwnerTypedData(data, signature) { return getAddress(verifyTypedData(data.domain, data.types, data.message, signature)) === ownerAddress; },
      async balances(assets) {
        ready();
        const entries = Object.entries(assets).filter(([, asset]) => asset.contract);
        const balances = await account.getTokenBalances(entries.map(([, asset]) => asset.contract));
        return Object.fromEntries(entries.map(([symbol, asset]) => [symbol, { address, contract: getAddress(asset.contract), atomic: String(balances[asset.contract] ?? balances[getAddress(asset.contract)] ?? 0n), decimals: asset.decimals }]));
      },
      async quoteTransfer({ token, recipient, amount }) { ready(); return account.quoteTransfer({ token: getAddress(token), recipient: getAddress(recipient), amount: BigInt(amount) }); },
      async quoteSponsoredTransfer(options) { ready(); if (feeMode !== 'sponsored') throw new Error('This session pays gas with tokens; use quoteTransfer'); return this.quoteTransfer(options); },
      async submitTransfer({ token, recipient, amount }, { allowTestnetBroadcast = false, allowLocalBroadcast = false } = {}) {
        ready();
        const isLoopback = value => { try { const url = new URL(value);return url.protocol === 'http:' && ['127.0.0.1','localhost'].includes(url.hostname); } catch { return false; } };
        const local = allowLocalBroadcast && chainId === 31337 && [provider, bundlerUrl, paymasterUrl].every(isLoopback);
        if (!local && (!allowTestnetBroadcast || chainId !== 84532)) throw new Error('Only an explicitly enabled test proof may broadcast');
        return account.transfer({ token: getAddress(token), recipient: getAddress(recipient), amount: BigInt(amount) });
      },
      async submitSponsoredTransfer(options, permissions) { ready(); if (feeMode !== 'sponsored') throw new Error('Token-paid transfers are not fully sponsored; use submitTransfer'); return this.submitTransfer(options, permissions); },
      dispose() { if (!disposed) { disposed = true; wdk.dispose(); ownerManager.dispose(); } },
    };
  } catch (error) { wdk.dispose(); ownerManager.dispose(); throw error; }
}

export async function restoreWalletSession(seed, descriptor, options = {}) {
  if (descriptor?.schema !== 'midori.safe-account' || descriptor.version !== 1) throw new Error('Unsupported account descriptor');
  if (descriptor.accountType !== 'safe-erc4337' || descriptor.threshold !== 1) throw new Error('Unsupported account configuration');
  for (const key of Object.keys(SAFE_CONFIG)) if (descriptor[key] !== SAFE_CONFIG[key]) throw new Error(`Account configuration changed: ${key}`);
  for (const key of Object.keys(SDK_VERSIONS)) if (descriptor.sdk?.[key] !== SDK_VERSIONS[key]) throw new Error(`SDK version mismatch: ${key}`);
  const session = await createWalletSession(seed, { ...options, chainId: descriptor.chainId, index: descriptor.index });
  for (const key of ['address', 'ownerAddress', 'derivationPath', 'factoryAddress', 'factoryData']) {
    if (session.descriptor[key] !== descriptor[key]) { session.dispose(); throw new Error(`Recovery mismatch: ${key}`); }
  }
  return session;
}

export async function exportOwnerKey(seed, descriptor) {
  const session = await restoreWalletSession(seed, descriptor);
  try {
    const owner = HDNodeWallet.fromSeed(seed).derivePath(descriptor.derivationPath);
    if (getAddress(owner.address) !== getAddress(descriptor.ownerAddress)) throw new Error('Recovery owner does not match');
    return { address: descriptor.address, ownerAddress: owner.address, ownerPrivateKey: owner.privateKey, derivationPath: descriptor.derivationPath, descriptor: structuredClone(descriptor) };
  } finally { session.dispose(); }
}

export function readOnlyAccount(descriptor, provider) {
  return WalletAccountReadOnlyEvmErc4337.fromSafeAddress(descriptor.address, { chainId: descriptor.chainId, provider, safeModulesVersion: descriptor.safeModulesVersion, bundlerUrl: `https://api.candide.dev/public/v3/${descriptor.chainId}`, paymasterUrl: `https://api.candide.dev/public/v3/${descriptor.chainId}`, isSponsored: true });
}

export function decodeTransferLog(log, chainId) {
  const decoded = tokenInterface.parseLog(log);
  if (!decoded || decoded.name !== 'Transfer') throw new Error('Not an ERC-20 transfer event');
  const logIndex = Number(BigInt(log.logIndex ?? log.index));
  if (!Number.isSafeInteger(logIndex) || logIndex < 0 || !/^0x[0-9a-fA-F]{64}$/.test(log.transactionHash || '') || !/^0x[0-9a-fA-F]{64}$/.test(log.blockHash || '')) throw new Error('Invalid event identity');
  return { id: `${chainId}:${log.transactionHash.toLowerCase()}:${logIndex}`, chainId, transactionHash: log.transactionHash, logIndex, blockHash: log.blockHash, blockNumber: Number(BigInt(log.blockNumber)), contract: getAddress(log.address), from: getAddress(decoded.args.from), to: getAddress(decoded.args.to), amount: decoded.args.value.toString(), canonical: !log.removed };
}

export function reconcileTransferEvents(existing, incoming, { canonicalBlockHashes = {} } = {}) {
  const events = new Map(existing.map(event => [event.id, { ...event }]));
  for (const event of incoming) events.set(event.id, { ...event });
  return [...events.values()].map(event => ({ ...event, canonical: event.canonical && (!canonicalBlockHashes[event.blockNumber] || canonicalBlockHashes[event.blockNumber].toLowerCase() === event.blockHash.toLowerCase()) })).sort((a, b) => a.blockNumber - b.blockNumber || a.logIndex - b.logIndex);
}

export * from './payment.js';
