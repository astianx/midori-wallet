export class WalletConnect {
  async init() { throw Object.assign(new Error('WalletConnect is not enabled'), { code: 'CAPABILITY_UNAVAILABLE' }); }
}
