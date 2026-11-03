import { getAddress, ZeroAddress } from 'ethers';
import { SafeAccountV0_3_0 } from 'abstractionkit';
import { exactKeys, requireValue } from './errors.js';

export const SAFE_CONFIG = Object.freeze({
  safeModulesVersion: '0.3.0', entryPoint: '0x0000000071727De22E5E9d8BAf0edAc6f37da032',
  saltNonce: '0x69b348339eea4ed93f9d11931c3b894c8f9d8c7663a053024b11cb7eb4e5a1f6',
  module: '0x75cf11467937ce3F2f357CE24ffc3DBF8fD5c226', setup: '0x2dd68b007B46fBe91B9A7c3EDa5A7a1063cB5b47',
});
export const SDK_VERSIONS = Object.freeze({ core: '1.0.0-beta.22', evm: '1.0.0-beta.19', erc4337: '1.0.0-beta.22', abstractionkit: '0.4.0', ethers: '6.17.0' });

export function validateDescriptor(value) {
  exactKeys(value, ['schema', 'version', 'chainId', 'accountType', 'index', 'derivationPath', 'ownerAddress', 'address', 'factoryAddress', 'factoryData', 'threshold', ...Object.keys(SAFE_CONFIG), 'sdk']);
  requireValue(value.schema === 'midori.safe-account' && value.version === 1 && value.chainId === 8453 && value.accountType === 'safe-erc4337' && value.threshold === 1, 'Unsupported account descriptor');
  requireValue(Number.isSafeInteger(value.index) && value.index >= 0 && value.index <= 2147483647, 'Invalid account index');
  requireValue(value.derivationPath === `m/44'/60'/0'/0/${value.index}`, 'Invalid derivation path');
  exactKeys(value.sdk, Object.keys(SDK_VERSIONS));
  requireValue(Object.entries(SAFE_CONFIG).every(([key, expected]) => value[key] === expected), 'Unsupported Safe configuration');
  requireValue(Object.entries(SDK_VERSIONS).every(([key, expected]) => value.sdk[key] === expected), 'Unsupported SDK versions');
  try {
    for (const key of ['ownerAddress', 'address', 'factoryAddress']) requireValue(getAddress(value[key]) === value[key] && value[key] !== ZeroAddress, 'Expected nonzero checksum address');
    const overrides = { c2Nonce: BigInt(value.saltNonce), entrypointAddress: value.entryPoint, safe4337ModuleAddress: value.module, safeModuleSetupAddress: value.setup };
    const [factoryAddress, factoryData] = SafeAccountV0_3_0.createFactoryAddressAndData([value.ownerAddress], overrides);
    const address = SafeAccountV0_3_0.createAccountAddress([value.ownerAddress], overrides);
    requireValue(getAddress(address) === value.address && getAddress(factoryAddress) === value.factoryAddress && factoryData === value.factoryData, 'Descriptor derivation mismatch');
  } catch (error) {
    if (error.status === 400) throw error;
    requireValue(false, 'Invalid account descriptor');
  }
  return value;
}

export function parseAtomic(value) {
  requireValue(typeof value === 'string' && /^(0|[1-9][0-9]{0,77})$/.test(value), 'Amount must be an atomic integer string');
  requireValue(BigInt(value) < 2n ** 256n, 'Amount exceeds uint256');
  return value;
}
