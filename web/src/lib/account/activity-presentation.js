import { Amount } from './domain.js';
import { rampAmount, rampNetwork } from './ramp-presentation.js';

// Read models only: source identifiers never imply settlement between sources.
export function paymentRecord(transaction, wallet) {
  return { ...transaction, source: 'payment', walletId: wallet.crypto._id, reference: transaction.id,
    id: `payment:${wallet.crypto._id}:${transaction.id}`, symbol: wallet.crypto.symbol,
    timestamp: new Date(transaction.timestamp), state: transaction.chainStatus,
    kind: transaction.incoming ? 'receive' : 'send', amount: `${transaction.incoming ? '+' : '−'}${transaction.amount} ${wallet.crypto.symbol}` };
}
export function orderRecord(order) {
  return { ...order, source: 'ramp', reference: order.id, id: `ramp:${order.id}`, symbol: order.cryptoCurrency,
    timestamp: new Date(order.createdAt), state: order.status,
    amount: order.kind === 'buy' ? rampAmount(order.fiatAmount, order.fiatCurrency) : rampAmount(order.cryptoAmount, order.cryptoCurrency) };
}
export function activityGroup(item) {
  if (['confirmed', 'completed'].includes(item.state)) return 'completed';
  if (item.state === 'refunded') return 'refunded';
  if (['reverted', 'failed', 'expired', 'cancelled', 'rejected'].includes(item.state)) return 'failed';
  if (['ambiguous', 'reorganized'].includes(item.state)) return 'checking';
  return 'pending';
}
export function activityTitle(item, t) {
  return t(({ send: 'Sent', receive: 'Received', buy: 'Buy {symbol}', sell: 'Sell {symbol}' })[item.kind], { symbol: item.symbol });
}
export function activityStatus(item, t) {
  if (item.state === 'created') return t('Started');
  if (item.state === 'awaiting_payment') return t('Awaiting payment');
  return t(({ completed: 'Completed', refunded: 'Refunded', failed: 'Not completed', checking: 'Checking', pending: 'Pending' })[activityGroup(item)]);
}
export function activityMessage(item, t) {
  if (item.state === 'ambiguous') return t('We are checking this payment. Do not send it again.');
  if (item.state === 'reorganized') return t('The network is updating this payment. We will keep checking it.');
  if (item.state === 'reverted') return t('The recipient did not receive this payment. The network may have charged a cost.');
  if (item.state === 'refunded') return t('Check your original payment method.');
  if (item.source === 'ramp' && ['failed', 'expired'].includes(item.state)) return t('Not completed');
  if (item.source === 'ramp') return t(item.state === 'completed' ? 'Your wallet balance updates separately.' : 'We have not confirmed completion. Check the latest order status.');
  if (['expired', 'cancelled', 'rejected'].includes(item.state)) return t('Payment not sent');
  return t(item.state === 'confirmed' ? 'The recipient received the approved amount.' : 'We are checking this payment. Do not send it again.');
}
export function activityRows(item, t, date) {
  const rows = [{ label: t('Status'), value: activityStatus(item, t) }, { label: t('Date'), value: date(item.timestamp) }];
  if (item.source === 'ramp') {
    rows.push({ label: t('You pay'), value: item.kind === 'buy' ? rampAmount(item.fiatAmount, item.fiatCurrency) : rampAmount(item.cryptoAmount, item.cryptoCurrency), financial: true },
      { label: t('Receive'), value: item.kind === 'buy' ? rampAmount(item.cryptoAmount, item.cryptoCurrency) : rampAmount(item.fiatAmount, item.fiatCurrency), financial: true });
    rows.push({ label: t('Fee'), value: '—', financial: true });
    if (item.paymentMethod) rows.push({ label: t('Payment method'), value: item.paymentMethod });
    if (item.walletAddress) rows.push({ label: t('Wallet address'), value: item.walletAddress, technical: true });
  } else {
    if (item.incoming && item.from) rows.push({ label: t('From'), value: item.from });
    if (!item.incoming && item.recipientTag) rows.push({ label: t('To'), value: `$${item.recipientTag.replace(/^[$@]/, '')}` });
    if (item.to) rows.push({ label: t(item.incoming ? 'Wallet address' : 'Recipient'), value: item.to });
    if (item.origin === 'midori' && item.midoriFeeAtomic != null) rows.push({ label: t('Midori fee'), value: `${new Amount(item.midoriFeeAtomic, 6)} ${item.symbol}`, financial: true });
    if (item.feeMode === 'sponsored') rows.push({ label: t('Network cost'), value: t('Sponsored by Midori') });
    else if (item.networkFee != null) rows.push({ label: t('Network cost'), value: `${item.networkFee} ${item.symbol}`, financial: true });
    // A ceiling remains a ceiling; never present it as the cost actually charged.
    else if (item.maxNetworkFee != null) rows.push({ label: t('Network cost'), value: t('Maximum network cost: {amount} {symbol}', { amount: String(item.maxNetworkFee), symbol: item.symbol }), financial: true });
  }
  rows.push({ label: t('Network'), value: item.source === 'ramp' ? rampNetwork(item.environment) : 'Base', technical: true }, { label: t('Reference'), value: item.reference });
  return rows;
}
export function activityQuery(item) {
  return { source: item.source, entry: item.reference, ...(item.source === 'payment' ? { wallet: item.walletId } : {}) };
}
export function filterActivity(records, { symbol = '', kind = '', state = '' } = {}) {
  return records.filter(item => (!symbol || item.symbol === symbol) && (!kind || item.kind === kind) && (!state || activityGroup(item) === state));
}
