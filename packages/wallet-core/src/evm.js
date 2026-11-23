import { HDNodeWallet, Interface, getAddress, hexlify, toUtf8Bytes, toUtf8String, verifyTypedData } from 'ethers';
import { SafeAccountV0_3_0, calculateUserOperationMaxGasCost, createUserOperationHash } from 'abstractionkit';
import { stablecoinNetwork, networkAsset } from '../../../config/stablecoin-networks.js';
import { paymentOperation } from './payment.js';
import { SAFE_CONFIG } from './safe-config.js';

export const evmToken = new Interface(['function transfer(address to,uint256 value) returns(bool)', 'function balanceOf(address) view returns(uint256)', 'event Transfer(address indexed from,address indexed to,uint256 value)']);
export const EVM_MAX_FEE = 10n ** 18n;
export const EVM_MAX_RAW_LENGTH = 16384;
export const evmSame = (a, b) => typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();
const check = (condition, message = 'Payment changed') => { if (!condition) throw new Error(message); };
export function evmRecipient(value, sender, contract) {
  const address = getAddress(value);
  check(!/^0x0{40}$/i.test(address) && !evmSame(address, sender) && !evmSame(address, contract), 'Invalid recipient');
  return address;
}
export function evmAccount(descriptor) {
  check(descriptor?.accountType === 'safe-erc4337' && descriptor.threshold === 1);
  for (const [key, value] of Object.entries(SAFE_CONFIG)) check(descriptor[key] === value, 'Account configuration changed');
  const options = { c2Nonce: BigInt(descriptor.saltNonce), entrypointAddress: descriptor.entryPoint, safe4337ModuleAddress: descriptor.module, safeModuleSetupAddress: descriptor.setup };
  const smart = SafeAccountV0_3_0.initializeNewAccount([descriptor.ownerAddress], options);
  const [factory, data] = SafeAccountV0_3_0.createFactoryAddressAndData([descriptor.ownerAddress], options);
  check(evmSame(smart.accountAddress, descriptor.address) && evmSame(factory, descriptor.factoryAddress) && data === descriptor.factoryData, 'Recovery mismatch');
  return smart;
}
export function validateEvmPayment(quote, descriptor, expectedAsset, { allowExpired = false } = {}) {
  const network = stablecoinNetwork(quote?.network), asset = networkAsset(network, quote?.contract);
  check(network?.enabled && network.id !== 'base' && asset && quote.chainId === network.chainId && evmSame(quote.from, descriptor.address) && evmSame(asset.contract, expectedAsset.address || expectedAsset.contract) && quote.chainId === expectedAsset.chainId && quote.symbol === asset.symbol && quote.decimals === asset.decimals && quote.nativeSymbol === network.nativeSymbol);
  evmAccount(descriptor);
  const expiry = Date.parse(quote.expiresAt);
  check(Number.isFinite(expiry) && (allowExpired || (expiry > Date.now() && expiry <= Date.now() + 300000)), 'Quote expired');
  check(typeof quote.amountAtomic === 'string' && /^[1-9][0-9]{0,77}$/.test(quote.amountAtomic));
  const amount = BigInt(quote.amountAtomic);
  check(amount < 2n ** 256n);
  const to = evmRecipient(quote.to, descriptor.address, asset.contract), op = paymentOperation(quote.operation);
  check(evmSame(op.sender, descriptor.address) && typeof quote.nonce === 'string' && /^(0|[1-9][0-9]*)$/.test(quote.nonce) && op.nonce === BigInt(quote.nonce) && op.nonce < 2n ** 63n);
  check((!op.factory && !op.factoryData) || (evmSame(op.factory, descriptor.factoryAddress) && op.factoryData === descriptor.factoryData), 'Unexpected account deployment');
  check(!op.paymaster && !op.paymasterData && !op.paymasterVerificationGasLimit && !op.paymasterPostOpGasLimit, 'Unexpected paymaster');
  const data = evmToken.encodeFunctionData('transfer', [to, amount]);
  check(op.callData === SafeAccountV0_3_0.createAccountCallDataSingleTransaction({ to: asset.contract, value: 0n, data }), 'Only the approved token transfer is supported');
  check(op.callGasLimit > 0n && op.verificationGasLimit > 0n && op.preVerificationGas > 0n && op.callGasLimit + op.verificationGasLimit + op.preVerificationGas <= 10000000n && op.maxFeePerGas > 0n && op.maxFeePerGas <= 10n ** 14n && op.maxPriorityFeePerGas <= op.maxFeePerGas, 'Invalid payment limits');
  const fee = calculateUserOperationMaxGasCost(op);
  check(fee <= EVM_MAX_FEE && quote.maxFeeAtomic === String(fee), 'Invalid maximum network cost');
  return op;
}
function signingData(quote, descriptor) {
  return SafeAccountV0_3_0.getUserOperationEip712Data(paymentOperation(quote.operation), BigInt(quote.chainId), { validAfter: 0n, validUntil: BigInt(Math.floor(Date.parse(quote.expiresAt) / 1000)), entrypointAddress: descriptor.entryPoint, safe4337ModuleAddress: descriptor.module });
}
function wrapSignature(quote, owner, signature) {
  return SafeAccountV0_3_0.formatEip712SignaturesToUseroperationSignature([owner], [signature], { validAfter: 0n, validUntil: BigInt(Math.floor(Date.parse(quote.expiresAt) / 1000)) });
}
export async function signEvmTransfer(seed, descriptor, quote, asset) {
  const op = validateEvmPayment(quote, descriptor, asset);
  check(op.signature === '0x', 'Already signed operation');
  const owner = HDNodeWallet.fromSeed(seed).derivePath(descriptor.derivationPath);
  check(evmSame(owner.address, descriptor.ownerAddress), 'Recovery mismatch');
  const typed = signingData(quote, descriptor);
  const signature = await owner.signTypedData(typed.domain, typed.types, typed.messageValue);
  return hexlify(toUtf8Bytes(JSON.stringify({ version: 2, quote: { ...quote, operation: { ...quote.operation, signature: wrapSignature(quote, owner.address, signature) } } })));
}
function envelope(raw) {
  check(typeof raw === 'string' && /^0x(?:[0-9a-f]{2})+$/i.test(raw) && raw.length <= EVM_MAX_RAW_LENGTH, 'Invalid signed operation');
  const value = JSON.parse(toUtf8String(raw));
  check(value?.version === 2 && Object.keys(value).length === 2 && value.quote, 'Unsupported signed operation');
  return value.quote;
}
export function evmTransactionHash(raw) {
  const quote = envelope(raw), network = stablecoinNetwork(quote.network);
  check(network?.chainId === quote.chainId);
  return createUserOperationHash(paymentOperation(quote.operation), SAFE_CONFIG.entryPoint, BigInt(network.chainId));
}
export function inspectEvmTransfer(raw, network, descriptor) {
  const quote = envelope(raw), asset = networkAsset(network, quote.contract);
  check(quote.network === network?.id && asset);
  const op = validateEvmPayment(quote, descriptor, { ...asset, chainId: network.chainId }, { allowExpired: true });
  check(/^0x[0-9a-f]{154}$/i.test(op.signature), 'Invalid signature');
  const signature = `0x${op.signature.slice(-130)}`, typed = signingData(quote, descriptor);
  check(evmSame(verifyTypedData(typed.domain, typed.types, typed.messageValue, signature), descriptor.ownerAddress) && wrapSignature(quote, descriptor.ownerAddress, signature) === op.signature, 'Invalid owner signature');
  return { quote, operation: op, asset, to: quote.to, amountAtomic: quote.amountAtomic, hash: evmTransactionHash(raw) };
}
