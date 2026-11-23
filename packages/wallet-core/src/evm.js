import { HDNodeWallet, Interface, Transaction, getAddress, keccak256 } from 'ethers';
import { stablecoinNetwork, networkAsset } from '../../../config/stablecoin-networks.js';

export const evmTransactionHash = raw => keccak256(raw);
export const evmToken = new Interface(['function transfer(address to,uint256 value) returns(bool)', 'function balanceOf(address) view returns(uint256)', 'event Transfer(address indexed from,address indexed to,uint256 value)']);
export const EVM_MAX_FEE = 10n ** 18n;
export const evmSame = (a, b) => typeof a === 'string' && typeof b === 'string' && a.toLowerCase() === b.toLowerCase();
export function evmRecipient(value, sender, contract) {
  const address = getAddress(value);
  if (/^0x0{40}$/i.test(address) || evmSame(address, sender) || evmSame(address, contract)) throw new Error('Invalid recipient');
  return address;
}
export function evmTransaction(quote, descriptor, expectedAsset, now = Date.now()) {
  const network = stablecoinNetwork(quote?.network);
  const asset = networkAsset(network, quote?.contract);
  if (!network?.enabled || network.accountType === 'safe' || !asset || quote.chainId !== network.chainId || !evmSame(quote.from, descriptor.ownerAddress) || !evmSame(asset.contract, expectedAsset.address || expectedAsset.contract) || quote.chainId !== expectedAsset.chainId || quote.symbol !== asset.symbol || quote.decimals !== asset.decimals) throw new Error('Payment changed');
  const expiry = Date.parse(quote.expiresAt);
  if (!Number.isFinite(expiry) || expiry <= now || expiry > now + 300000) throw new Error('Quote expired');
  const positive = value => { if (typeof value !== 'string' || !/^[1-9][0-9]{0,77}$/.test(value)) throw new Error('Invalid amount'); return BigInt(value); };
  const amount = positive(quote.amountAtomic), gasLimit = positive(quote.gasLimit), gasPrice = positive(quote.gasPrice);
  if (amount >= 2n ** 256n || gasLimit > 1000000n || gasPrice > 10n ** 14n || gasLimit * gasPrice > EVM_MAX_FEE || String(gasLimit * gasPrice) !== quote.maxFeeAtomic || !Number.isSafeInteger(quote.nonce) || quote.nonce < 0 || quote.nonce > 0xffffffff) throw new Error('Invalid payment limits');
  const to = evmRecipient(quote.to, quote.from, asset.contract);
  return { type: 0, chainId: network.chainId, nonce: quote.nonce, to: asset.contract, value: 0n, data: evmToken.encodeFunctionData('transfer', [to, amount]), gasLimit, gasPrice };
}
export async function signEvmTransfer(seed, descriptor, quote, asset) {
  const transaction = evmTransaction(quote, descriptor, asset);
  const owner = HDNodeWallet.fromSeed(seed).derivePath(descriptor.derivationPath);
  if (!evmSame(owner.address, descriptor.ownerAddress)) throw new Error('Recovery mismatch');
  return owner.signTransaction(transaction);
}
export function inspectEvmTransfer(raw, network, owner) {
  if (typeof raw !== 'string' || !/^0x[0-9a-f]+$/i.test(raw) || raw.length > 4096) throw new Error('Invalid transaction');
  const tx = Transaction.from(raw), asset = networkAsset(network, tx.to);
  if (!tx.isSigned() || tx.type !== 0 || !network?.enabled || network.accountType === 'safe' || tx.chainId !== BigInt(network.chainId) || !evmSame(tx.from, owner) || tx.value !== 0n || !asset || tx.gasLimit <= 0n || tx.gasLimit > 1000000n || !tx.gasPrice || tx.gasPrice > 10n ** 14n || tx.gasLimit * tx.gasPrice > EVM_MAX_FEE) throw new Error('Invalid transaction');
  const parsed = evmToken.parseTransaction({ data: tx.data });
  if (parsed?.name !== 'transfer' || parsed.args[1] <= 0n) throw new Error('Only token transfers are supported');
  const to = evmRecipient(parsed.args[0], owner, asset.contract);
  if (evmToken.encodeFunctionData('transfer', [to, parsed.args[1]]) !== tx.data) throw new Error('Unexpected calldata');
  return { tx, asset, to, amountAtomic: parsed.args[1].toString(), hash: keccak256(raw) };
}
