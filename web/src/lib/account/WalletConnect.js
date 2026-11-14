import { EventEmitter } from 'events';
import { Core } from '@walletconnect/core';
import { WalletKit } from '@reown/walletkit';
import { buildApprovedNamespaces, getSdkError, parseUri } from '@walletconnect/utils';
import { WALLETCONNECT_CHAIN, WALLETCONNECT_METHODS, validateWalletConnectRequest, walletConnectError } from './walletconnect-policy.js';

export class WalletConnect extends EventEmitter {
  #account;
  #client;
  #projectId;
  #pairing;
  #disposed = false;
  #handling = new Set();
  #proposals = new Map();

  constructor({ account, client, projectId }) {
    super();
    this.#account = account;
    this.#client = client;
    this.#projectId = projectId;
  }

  async init() {
    if (!this.#client) {
      const projectId = this.#projectId || import.meta.env.VITE_WALLETCONNECT_ID;
      if (!/^[a-f0-9]{32}$/i.test(projectId || '')) throw walletConnectError('Configure VITE_WALLETCONNECT_ID');
      const core = new Core({ projectId, telemetryEnabled: false });
      this.#client = await WalletKit.init({
        core,
        metadata: {
          name: 'Midori Wallet', description: 'USDT and USDC on Base',
          url: 'https://midoriwallet.com', icons: ['https://midoriwallet.com/wallet/favicon-32x32.png'],
        },
      });
    }
    this.#client.on('session_proposal', this.#onProposal);
    this.#client.on('session_request', this.#onRequest);
    this.#client.on('session_delete', this.#onDelete);
    this.#account.once('locked', this.#onLock);
    if (this.#account.isLocked) await this.dispose();
    return this;
  }

  get session() {
    const address = this.#account.recoveryDescriptor?.address;
    if (this.#disposed || !address || this.#account.isLocked) return undefined;
    return Object.values(this.#client.getActiveSessions()).find(session => session.expiry * 1000 > Date.now() && session.namespaces.eip155?.accounts.some(account => account.toLowerCase() === `${WALLETCONNECT_CHAIN}:${address}`.toLowerCase()));
  }

  async pair(value) {
    this.#assertUnlocked();
    if (this.#pairing) throw walletConnectError('A connection is already pending');
    if (this.session) throw walletConnectError('Disconnect the current dApp first');
    const uri = typeof value === 'string' ? value.trim() : '';
    let parsed;
    try { parsed = parseUri(uri); } catch { throw walletConnectError('Invalid URI', -32602); }
    if (parsed.version !== 2 || !/^[a-f0-9]{64}$/i.test(parsed.topic || '') || !/^[a-f0-9]{64}$/i.test(parsed.symKey || '')) throw walletConnectError('Invalid URI', -32602);
    const proposal = new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.#pairing = undefined; reject(walletConnectError('Please try again with a new connection URI')); }, 90000);
      this.#pairing = { topic: parsed.topic, resolve, reject, timer };
    });
    try { await this.#client.pair({ uri }); }
    catch (error) { clearTimeout(this.#pairing?.timer); this.#pairing?.reject(error); this.#pairing = undefined; }
    return proposal;
  }

  reviewProposal(proposal) {
    this.#assertUnlocked();
    if (!this.#proposals.has(proposal.id) || proposal.params.expiryTimestamp * 1000 <= Date.now()) throw walletConnectError('Please try again with a new connection URI');
    const verification = proposal.verifyContext?.verified;
    if (verification?.isScam || verification?.validation === 'INVALID') throw walletConnectError('This domain is flagged as malicious and potentially harmful.');
    const metadata = proposal.params.proposer.metadata;
    let url;
    try { url = new URL(metadata.url); } catch { throw walletConnectError('Invalid dApp URL'); }
    if (url.username || url.password || !(url.protocol === 'https:' || url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname))) throw walletConnectError('Invalid dApp URL');
    const address = this.#account.recoveryDescriptor.address;
    const namespaces = buildApprovedNamespaces({
      proposal: proposal.params,
      supportedNamespaces: { eip155: { chains: [WALLETCONNECT_CHAIN], methods: WALLETCONNECT_METHODS, events: ['accountsChanged', 'chainChanged'], accounts: [`${WALLETCONNECT_CHAIN}:${address}`] } },
    });
    return { name: metadata.name, url: url.origin, address, namespaces, verified: verification?.validation === 'VALID' };
  }

  async approveSession(proposal) {
    const { namespaces } = this.reviewProposal(proposal);
    const session = await this.#client.approveSession({ id: proposal.id, namespaces });
    this.#proposals.delete(proposal.id);
    if (this.#disposed || this.#account.isLocked) {
      await this.#client.disconnectSession({ topic: session.topic, reason: getSdkError('USER_DISCONNECTED') });
      throw walletConnectError('Wallet is locked', 5000);
    }
    return session;
  }

  async rejectProposal(proposal) {
    if (!proposal || !this.#proposals.has(proposal.id)) return;
    this.#proposals.delete(proposal.id);
    await this.#client.rejectSession({ id: proposal.id, reason: getSdkError('USER_REJECTED') });
  }

  validateRequest(event) {
    this.#assertUnlocked();
    return validateWalletConnectRequest(event, this.session, this.#account.recoveryDescriptor.address);
  }

  async getPendingSessionRequests() {
    this.#handling.clear();
    for (const event of this.#client.getPendingSessionRequests()) await this.#onRequest(event);
  }

  async reviewTransfer(event) {
    const { transfer } = this.validateRequest(event);
    if (!transfer) throw walletConnectError('Not supported', 5101);
    const wallet = this.#account.wallets().find(wallet => wallet.crypto.address?.toLowerCase() === transfer.contract);
    if (!wallet) throw walletConnectError('Not supported', 5101);
    const key = `midori:wc:payment:${event.topic}:${event.id}`;
    const storage = this.#client.core.storage;
    const fingerprint = JSON.stringify(event);
    let record = await storage.getItem(key);
    if (record && record.fingerprint !== fingerprint) throw walletConnectError('WalletConnect request changed');
    if (record?.intentId) return wallet.payment(record.intentId);
    if (!record) {
      record = { fingerprint, idempotencyKey: crypto.randomUUID() };
      await storage.setItem(key, record);
    }
    const feeMode = this.#account.paymentConfig.preferredMode || 'token';
    const intent = await wallet.reviewPayment({
      amountAtomic: transfer.amountAtomic, recipient: { kind: 'address', address: transfer.recipient },
      feeMode, feeToken: feeMode === 'token' ? transfer.contract : null,
      maxFeeAtomic: feeMode === 'token' ? this.#account.paymentConfig.maxFeeAtomic : '0', idempotencyKey: record.idempotencyKey,
    });
    await storage.setItem(key, { ...record, intentId: intent.id });
    return intent;
  }

  async resolveSessionRequest(event, result) {
    this.validateRequest(event);
    await this.#client.respondSessionRequest({ topic: event.topic, response: { id: event.id, jsonrpc: '2.0', result } });
    this.#handling.delete(`${event.topic}:${event.id}`);
  }

  async rejectSessionRequest(event, message = 'User rejected', code = 5000) {
    try { await this.#client.respondSessionRequest({ topic: event.topic, response: { id: event.id, jsonrpc: '2.0', error: { code: typeof code === 'number' ? code : 5000, message } } }); }
    finally { this.#handling.delete(`${event.topic}:${event.id}`); }
  }

  async disconnectSession() {
    const session = this.session;
    if (!session) return;
    await this.#client.disconnectSession({ topic: session.topic, reason: getSdkError('USER_DISCONNECTED') });
    this.#handling.clear();
    this.emit('disconnect');
  }

  async dispose() {
    if (this.#disposed) return;
    const sessions = Object.values(this.#client.getActiveSessions());
    this.#disposed = true;
    clearTimeout(this.#pairing?.timer);
    this.#pairing?.reject(walletConnectError('Wallet is locked'));
    this.#pairing = undefined;
    this.#client.off('session_proposal', this.#onProposal);
    this.#client.off('session_request', this.#onRequest);
    this.#client.off('session_delete', this.#onDelete);
    this.#account.off('locked', this.#onLock);
    await Promise.allSettled([
      ...sessions.map(session => this.#client.disconnectSession({ topic: session.topic, reason: getSdkError('USER_DISCONNECTED') })),
      ...[...this.#proposals.values()].map(proposal => this.rejectProposal(proposal)),
    ]);
    this.#handling.clear();
    this.removeAllListeners();
  }

  #assertUnlocked() {
    if (this.#disposed || this.#account.isLocked || !this.#account.recoveryDescriptor?.address) throw walletConnectError('Wallet is locked');
  }

  #onLock = () => { void this.dispose().catch(() => {}); };
  #onDelete = () => { this.#handling.clear(); this.emit('disconnect'); };
  #onProposal = proposal => {
    if (this.#disposed) return;
    this.#proposals.set(proposal.id, proposal);
    if (this.#pairing?.topic !== proposal.params.pairingTopic) { void this.rejectProposal(proposal).catch(() => {}); return; }
    const pairing = this.#pairing;
    clearTimeout(pairing.timer);
    this.#pairing = undefined;
    pairing.resolve(proposal);
  };
  #onRequest = async event => {
    const key = `${event.topic}:${event.id}`;
    if (this.#handling.has(key)) return;
    try {
      const request = this.validateRequest(event);
      if (['eth_accounts', 'eth_requestAccounts'].includes(request.method)) {
        await this.resolveSessionRequest(event, [this.#account.recoveryDescriptor.address]);
      } else if (this.listenerCount('request')) {
        if (this.#handling.size) throw walletConnectError('Another request is awaiting confirmation');
        this.#handling.add(key);
        this.emit('request', structuredClone(event));
      }
    } catch (error) { await this.rejectSessionRequest(event, error.message, error.code).catch(() => {}); }
  };
}
