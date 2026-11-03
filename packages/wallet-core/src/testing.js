import { sealVault, openVault } from './vault.js';
import { createWalletSession, restoreWalletSession, noNetworkProvider } from './index.js';

export const PUBLIC_TEST_VECTOR = Object.freeze({
  id: 'bip39-abandon-vector-0',
  mnemonic: 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about',
  ownerAddress: '0x9858EfFD232B4033E47d90003D41EC34EcaEda94',
  address: '0x7E9faA956191d0e766Cc0702515715150a76F6F2',
  warning: 'Public test vector. Never deposit funds or use this phrase for a real wallet.',
});

export async function runOfflineCompatibility({ runtime = 'node', chainId = 84532 } = {}) {
  const started = performance.now();
  let requests = 0;
  const provider = noNetworkProvider(() => { requests += 1; });
  const session = await createWalletSession(PUBLIC_TEST_VECTOR.mnemonic, { chainId, provider });
  let restored;
  try {
    const descriptor = JSON.parse(JSON.stringify(session.descriptor));
    const message = 'Midori F1 public compatibility fixture. This message authorizes no funds or account access.';
    const signature = await session.signMessage(message);
    const typedData = { domain: { name: 'Midori F1', version: '1', chainId }, types: { Compatibility: [{ name: 'purpose', type: 'string' }] }, message: { purpose: 'Public runtime proof; no transaction authorization' } };
    const typedSignature = await session.signTypedData(typedData);
    session.dispose();
    restored = await restoreWalletSession(PUBLIC_TEST_VECTOR.mnemonic, descriptor, { provider });
    const restoredSignature = await restored.signMessage(message);
    const vault = await sealVault(PUBLIC_TEST_VECTOR.mnemonic, descriptor, 'public-fixture-password-only');
    const portable = JSON.parse(JSON.stringify(vault));
    const unlocked = await openVault(portable, 'public-fixture-password-only');
    const vaultSession = await restoreWalletSession(unlocked, portable.descriptor, { provider });
    const vaultSignature = await vaultSession.signMessage(message);vaultSession.dispose();
    let tamperedVaultRejected = false;
    try { await openVault({ ...portable, descriptor: { ...portable.descriptor, address: portable.descriptor.ownerAddress } }, 'public-fixture-password-only'); } catch { tamperedVaultRejected = true; }
    const checks = {
      ownerMatchesVector: descriptor.ownerAddress === PUBLIC_TEST_VECTOR.ownerAddress,
      safeMatchesVector: descriptor.address === PUBLIC_TEST_VECTOR.address,
      ownerDifferentFromSafe: descriptor.ownerAddress !== descriptor.address,
      signatureValid: restored.verifyOwnerMessage(message, signature),
      alteredMessageRejected: !restored.verifyOwnerMessage(message + ' altered', signature),
      typedSignatureValid: restored.verifyOwnerTypedData(typedData, typedSignature),
      restoredSameAddress: restored.descriptor.address === descriptor.address,
      restoredSameSignature: restoredSignature === signature,
      sharedStablecoinAddress: restored.receiveAddress('USDT') === restored.receiveAddress('USDC') && restored.receiveAddress('USDT') === descriptor.address,
      encryptedVaultRestored: vaultSignature === signature,
      tamperedVaultRejected,
      networkRequests: requests === 0,
    };
    return { runtime, chainId, vector: PUBLIC_TEST_VECTOR.id, warning: PUBLIC_TEST_VECTOR.warning, passed: Object.values(checks).every(Boolean), checks, descriptor, receiveAddresses: { USDT: restored.receiveAddress('USDT'), USDC: restored.receiveAddress('USDC') }, durationMs: Math.round(performance.now() - started), networkRequests: requests, messageSignature: signature, typedSignature };
  } finally { session.dispose(); restored?.dispose(); }
}
