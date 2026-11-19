import WalletManagerBtc, { WalletAccountReadOnlyBtc } from '@tetherto/wdk-wallet-btc';
import { address as btcAddress, networks, Transaction } from 'bitcoinjs-lib';

export const BITCOIN_PATH = "m/84'/0'/0'/0/0";
export const BITCOIN_NETWORK = 'bitcoin';
export function validateBitcoinAddress(value) {
  if (typeof value !== 'string' || value.length > 90 || value !== value.trim()) return false;
  try { btcAddress.toOutputScript(value, networks.bitcoin); return true; } catch { return false; }
}
export function bitcoinBindingMessage(walletId, accountId, baseAddress, address) {
  return ['Midori Bitcoin account v1', walletId, accountId, baseAddress.toLowerCase(), BITCOIN_NETWORK, address, BITCOIN_PATH].join('\n');
}

export class BitcoinClient {
  constructor(request) { this.request = request; }
  async connect() {}
  async close() {}
  async reconnect() {}
  async getBalance(address) {
    const outputs = await this.listUnspent(address);
    const confirmed = outputs.reduce((total, output) => total + output.value, 0);
    if (!Number.isSafeInteger(confirmed) || confirmed < 0) throw new Error('Invalid Bitcoin balance');
    return { confirmed, unconfirmedOutgoing: 0 };
  }
  async listUnspent(address) {
    const values = await this.request(`address/${address}/utxo`);
    if (!Array.isArray(values)) throw new Error('Invalid Bitcoin outputs');
    return values.filter(item => item.status?.confirmed && /^[a-f0-9]{64}$/.test(item.txid) && Number.isSafeInteger(item.vout) && item.vout >= 0 && Number.isSafeInteger(item.value) && item.value > 0).map(item => ({ tx_hash: item.txid, tx_pos: item.vout, value: item.value, height: item.status.block_height }));
  }
  async getHistory(address) { return (await this.request(`address/${address}/txs`)).map(item => ({ tx_hash: item.txid, height: item.status?.block_height || 0 })); }
  async getBlockHeight() { return Number(await this.request('blocks/tip/height')); }
  async getTransaction(hash) { return this.request(`tx/${hash}/hex`); }
  async broadcast(raw) { return this.request('tx', raw); }
  async estimateFee(blocks = 1) {
    const rates = await this.request('fee-estimates');
    const rate = rates[String(blocks)] ?? Object.entries(rates).sort((a, b) => Math.abs(Number(a[0]) - blocks) - Math.abs(Number(b[0]) - blocks))[0]?.[1];
    if (!Number.isFinite(rate) || rate <= 0) throw new Error('Bitcoin fees unavailable');
    return rate / 100000;
  }
}

export function bitcoinReader(address, client) {
  if (!validateBitcoinAddress(address)) throw new Error('Invalid Bitcoin address');
  return new WalletAccountReadOnlyBtc(address, { network: BITCOIN_NETWORK, client });
}
export async function withBitcoinAccount(seed, client, operation, transactionMaxFee) {
  const manager = new WalletManagerBtc(seed, { network: BITCOIN_NETWORK, bip: 84, client, ...(transactionMaxFee === undefined ? {} : { transactionMaxFee: BigInt(transactionMaxFee) }) });
  try { const account = await manager.getAccount(0); if (account.path !== BITCOIN_PATH) throw new Error('Unexpected Bitcoin derivation'); return await operation(account); }
  finally { manager.dispose(); }
}
export const bitcoinTransactionId = raw => Transaction.fromHex(raw).getId();
export function bitcoinOutputs(raw) {
  return Transaction.fromHex(raw).outs.map(output => {
    let address;
    try { address = btcAddress.fromOutputScript(output.script, networks.bitcoin); } catch { address = null; }
    return { address, value: String(output.value) };
  });
}
