import { AbiCoder, getBytes, hashMessage, TypedDataEncoder } from 'ethers';

export const SAFE_MESSAGE_TYPES = { SafeMessage: [{ name: 'message', type: 'bytes' }] };
const magic6492 = '6492'.repeat(16);

export function safeMessageData(descriptor, method, data) {
  let digest;
  if (method === 'personal_sign') {
    digest = hashMessage(getBytes(data));
  } else if (['eth_signTypedData', 'eth_signTypedData_v4'].includes(method)) {
    const { EIP712Domain: _domain, ...types } = data.types;
    if (TypedDataEncoder.getPrimaryType(types) !== data.primaryType) throw new Error('Invalid typed data primary type');
    digest = TypedDataEncoder.hash(data.domain, types, data.message);
  } else throw new Error('Unsupported signing method');
  return { domain: { chainId: descriptor.chainId, verifyingContract: descriptor.address }, types: SAFE_MESSAGE_TYPES, message: { message: digest } };
}

export async function signSafeMessage(account, descriptor, method, data, deployed) {
  const signature = await account.signTypedData(safeMessageData(descriptor, method, data));
  if (deployed) return signature;
  return AbiCoder.defaultAbiCoder().encode(['address', 'bytes', 'bytes'], [descriptor.factoryAddress, descriptor.factoryData, signature]) + magic6492;
}
