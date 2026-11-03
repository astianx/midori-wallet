export type AssetSymbol = 'USDT' | 'USDC';
export type ReadState = 'loading' | 'fresh' | 'stale' | 'unavailable';
export type ConnectionState = 'online' | 'offline';
export interface Asset {
  readonly chainId: 8453;
  readonly symbol: AssetSymbol;
  readonly contract: string;
  readonly decimals: 6;
  readonly representation: string;
}
export interface Quantity {
  readonly value: bigint;
  readonly decimals: 6;
  toString(): string;
}
export interface Recipient { readonly address: string; readonly chainId: 8453; }
export interface AccountDescriptor {
  readonly schema: 'midori.safe-account';
  readonly version: 1;
  readonly chainId: 8453;
  readonly accountType: 'safe-erc4337';
  readonly address: string;
  readonly ownerAddress: string;
  readonly index: number;
  readonly derivationPath: string;
  readonly factoryAddress: string;
  readonly factoryData: string;
  readonly threshold: 1;
  readonly safeModulesVersion: '0.3.0';
  readonly entryPoint: string;
  readonly saltNonce: string;
  readonly module: string;
  readonly setup: string;
  readonly sdk: Readonly<Record<string, string>>;
}
export interface Balance {
  readonly asset: Asset;
  readonly amountAtomic?: string;
  readonly readAt?: string;
  readonly state: ReadState;
}
export interface TransferQuote {
  readonly asset: Asset;
  readonly recipient: Recipient;
  readonly amountAtomic: string;
  readonly feeAtomic: string;
  readonly feeAsset: Asset;
  readonly expiresAt: number;
}
export interface OwnerKeyExport {
  address: string;
  ownerAddress: string;
  ownerPrivateKey: string;
  derivationPath: string;
  descriptor: AccountDescriptor;
}
export interface AccountAdapterPort {
  create(seed: Uint8Array): Promise<AccountDescriptor>;
  restore(seed: Uint8Array, descriptor: AccountDescriptor): Promise<AccountDescriptor>;
  signMessage(message: string): Promise<string>;
  exportPrivateKeys(seed: Uint8Array, descriptor: AccountDescriptor): Promise<OwnerKeyExport>;
  lock(): void;
}
export interface ProtectedStoragePort {
  seal(pinEncryptedPayload: string): Promise<string>;
  open(protectedRecord: string): Promise<string>;
  remove?(protectedRecord: string): Promise<void>;
}
