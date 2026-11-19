import WalletManagerSpark, { WalletAccountReadOnlySpark } from '@tetherto/wdk-wallet-spark';
import { decodeSparkAddress } from '@buildonspark/spark-sdk';
import { decode as decodeInvoice } from '@tetherto/wdk-utils';
import { mapTransferToWalletTransfer } from '@buildonspark/spark-sdk/types';

export const SPARK_PATH = "m/44'/998'/0'/0/0";
export const SPARK_NETWORK = 'MAINNET';
const fail = code => Object.assign(new Error(code), { code });
export function validateSparkAddress(address) {
  if (typeof address !== 'string' || address.length > 256 || !address.startsWith('spark1')) return false;
  try { decodeSparkAddress(address, SPARK_NETWORK); return true; } catch { return false; }
}
export function sparkBindingMessage(walletId, accountId, address, depositAddress) {
  return ['Midori Spark account v1', walletId, accountId, SPARK_NETWORK, address, depositAddress, SPARK_PATH].join('\n');
}
export function lightningDetails(input, now = Date.now()) {
  if (typeof input !== 'string' || input.length > 20000) throw fail('INVALID_INVOICE');
  const invoice = input.trim().replace(/^lightning:/i, '');
  const result = decodeInvoice(invoice);
  if (!result.success || result.data.network !== 'bitcoin') throw fail('INVALID_INVOICE');
  const data = result.data, expiresAt = Number(data.timeExpireDate) * 1000;
  if (!Number.isFinite(expiresAt) || expiresAt <= now) throw fail('INVOICE_EXPIRED');
  const msat = data.millisatoshis == null ? null : BigInt(data.millisatoshis);
  if (msat !== null && (msat <= 0n || msat % 1000n !== 0n || msat / 1000n > 2100000000000000n)) throw fail('INVALID_INVOICE');
  return { invoice, amountAtomic: msat === null ? null : String(msat / 1000n), expiresAt: new Date(expiresAt).toISOString(), memo: String(data.tags.find(tag => tag.tagName === 'description')?.data || '') };
}
export function sparkReader(address, options = {}) {
  if (!validateSparkAddress(address)) throw fail('INVALID_RECIPIENT');
  return new WalletAccountReadOnlySpark(address, { ...options, network: SPARK_NETWORK });
}
export function normalizeSparkTransfer(transfer, identityKey) {
  const item = mapTransferToWalletTransfer(transfer, identityKey);
  if (![item.valueReceivedByWallet,item.valueSentByWallet].every(value => Number.isSafeInteger(value) && value >= 0)) throw fail('INVALID_REQUEST');
  if (!item.senders?.some(entry => entry.identityPublicKey === identityKey) && !item.receivers?.some(entry => entry.identityPublicKey === identityKey)) throw fail('INVALID_REQUEST');
  const incoming = item.valueReceivedByWallet >= item.valueSentByWallet;
  const value = Math.abs(item.valueReceivedByWallet - item.valueSentByWallet);
  if (!Number.isSafeInteger(value) || value < 0 || !/^[a-f0-9-]{36}$/.test(item.id)) throw fail('INVALID_REQUEST');
  const receiver = item.receivers?.find(entry => entry.identityPublicKey === identityKey);
  const status = (incoming && receiver ? receiver.status : item.status).replace(/^TRANSFER_(?:RECEIVER_)?STATUS_/, '');
  return { id: item.id, amountAtomic: String(value), incoming, timestamp: item.createdTime, status, from: item.senders?.map(entry => entry.identityPublicKey).join(', ') || item.senderIdentityPublicKey, to: incoming ? identityKey : item.receivers?.map(entry => entry.identityPublicKey).join(', ') || item.receiverIdentityPublicKey };
}
export async function openSparkSession(seed, { withdrawMaxFee, managerFactory = value => new WalletManagerSpark(value, { network: SPARK_NETWORK, syncAndRetry: true, enableLogging: false, ...(withdrawMaxFee === undefined ? {} : { withdrawMaxFee: BigInt(withdrawMaxFee) }) }) } = {}) {
  const manager = managerFactory(seed); let disposed = false;
  try {
    const account = await manager.getAccount(0);
    const descriptor = Object.freeze({ address: await account.getAddress(), identityKey: await account.getIdentityKey(), derivationPath: account.path, network: SPARK_NETWORK });
    if (descriptor.derivationPath !== SPARK_PATH || !validateSparkAddress(descriptor.address)) throw fail('ACCOUNT_RECOVERY_FAILED');
    const reader = sparkReader(descriptor.address);
    const ready = () => { if (disposed) throw fail('ACCOUNT_LOCKED'); };
    return {
      descriptor,
      async read(method, value) {
        ready();
        switch (method) {
          case 'balance': return reader.getBalance();
          case 'transfers': return reader.getTransfers(value);
          case 'transaction': return reader.getTransaction(value);
          case 'deposit': return account.getStaticDepositAddress();
          case 'deposits': return reader.getStaticDepositAddresses();
          case 'utxos': return reader.getUtxosForDepositAddress(value);
          case 'invoice': return account.createLightningInvoice(value);
          case 'invoiceStatus': return account.getLightningReceiveRequest(value);
          case 'paymentStatus': return account.getLightningSendRequest(value);
          case 'withdrawStatus': return account._wallet.getCoopExitRequest(value);
          case 'lightningFee': return account.quotePayLightningInvoice(value);
          case 'withdrawFee': return account.quoteWithdraw(value);
          case 'signBinding': return account.sign(value);
          case 'claimQuote': {
            const sdk = account._wallet;
            if (typeof sdk?.getClaimStaticDepositQuote !== 'function') throw fail('CAPABILITY_UNAVAILABLE');
            const quote = await sdk.getClaimStaticDepositQuote(value);
            if (!Number.isSafeInteger(quote?.creditAmountSats) || quote.creditAmountSats <= 0 || typeof quote.signature !== 'string') throw fail('QUOTE_UNAVAILABLE');
            return { creditAmountSats: quote.creditAmountSats, signature: quote.signature };
          }
          default: throw fail('CAPABILITY_UNAVAILABLE');
        }
      },
      async execute(kind, quote) {
        ready();
        const amount = BigInt(quote.amountAtomic);
        if (amount <= 0n || amount > 2100000000000000n) throw fail('INVALID_REQUEST');
        if (kind === 'spark') return account.sendTransaction({ to: quote.to, value: amount });
        if (kind === 'lightning') return account.payLightningInvoice({ invoice: quote.invoice, amountSatsToSend: Number(amount), maxFeeSats: Number(BigInt(quote.maxFeeAtomic)), transferId: quote.id });
        if (kind === 'withdraw') return account.withdraw({ onchainAddress: quote.to, amountSats: Number(amount), exitSpeed: 'MEDIUM', deductFeeFromWithdrawalAmount: false });
        if (kind === 'claim') {
          // WDK's unbounded claim fetches a new credit quote; bind the reviewed credit using its pinned Spark SDK.
          const sdk = account._wallet;
          if (typeof sdk?.claimStaticDeposit !== 'function') throw fail('CAPABILITY_UNAVAILABLE');
          return sdk.claimStaticDeposit({ transactionId: quote.transactionId, creditAmountSats: Number(amount), sspSignature: quote.signature });
        }
        throw fail('CAPABILITY_UNAVAILABLE');
      },
      async dispose() { if (!disposed) { disposed = true; try { await account.cleanupConnections(); } finally { manager.dispose(); } } },
    };
  } catch (error) { manager.dispose(); throw error; }
}
