import { createWalletSession, restoreWalletSession, readOnlyAccount, exportOwnerKey } from '@midori/wallet-core';
import { AccountError, Amount } from './domain.js';

export class AccountAdapter {
  #session;
  #generation = 0;
  async create(seed) { return this.#derive(() => createWalletSession(seed, { chainId: 8453 })); }
  async restore(seed, descriptor) { return this.#derive(() => restoreWalletSession(seed, descriptor)); }
  async #derive(operation) {
    const generation = this.#generation;
    try {
      this.#session?.dispose(); this.#session = null;
      const session = await operation();
      if (generation !== this.#generation) { session.dispose(); throw new Error('Account is locked'); }
      this.#session = session;
      return session.descriptor;
    }
    catch { throw new AccountError('ACCOUNT_RECOVERY_FAILED', 'The account descriptor does not match the recovery phrase or supported version.'); }
  }
  async signPayment(operation, expiresAt, payment) {
    if (!this.#session) throw new AccountError('ACCOUNT_LOCKED');
    return this.#session.signPayment(operation, expiresAt, payment);
  }
  async signMessage(message) {
    if (!this.#session) throw new AccountError('ACCOUNT_LOCKED');
    try { return await this.#session.signMessage(message); }
    catch { throw new AccountError('SIGNING_FAILED'); }
  }
  async exportPrivateKeys(seed, descriptor) {
    try { return await exportOwnerKey(seed, descriptor); }
    catch { throw new AccountError('KEY_EXPORT_FAILED', 'The account keys could not be exported.'); }
  }
  async balances(descriptor, assets, provider) {
    const transport = typeof provider === 'string' ? {
      async request({ method, params }) {
        if (!['eth_chainId', 'eth_call', 'eth_blockNumber'].includes(method)) throw new Error('Read-only provider');
        const response = await fetch(provider, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }), signal: AbortSignal.timeout(10000) });
        if (!response.ok) throw new Error('RPC unavailable');
        const result = await response.json();
        if (result.error || result.result == null) throw new Error('RPC unavailable');
        return result.result;
      },
    } : provider;
    try {
      const reader = readOnlyAccount(descriptor, transport);
      if (BigInt(await transport.request({ method: 'eth_chainId', params: [] })) !== BigInt(descriptor.chainId)) throw new Error('Unexpected RPC network');
      const values = await Promise.all(assets.map(asset => reader.getTokenBalance(asset.contract)));
      return assets.map((asset, index) => {
        const amount = new Amount(String(values[index]), asset.decimals);
        if (amount.value < 0n) throw new Error();
        return { ...asset, chainId: descriptor.chainId, address: descriptor.address, balanceAtomic: amount.value.toString() };
      });
    } catch { throw new AccountError('CHAIN_UNAVAILABLE', 'Base balances could not be updated.'); }
  }
  lock() { this.#generation++; this.#session?.dispose(); this.#session = null; }
}
