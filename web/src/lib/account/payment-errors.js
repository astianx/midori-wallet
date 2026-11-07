export function paymentError(error,t,{ symbol = 'USDT' } = {}) {
  const messages={
    PAYMENT_TRACKING_REQUIRED:'This payment is already authorized. Keep checking its status.',
    TAG_ACCOUNT_CONTROL_FAILED:'This tag must be verified again by its owner.',
    INVALID_RECIPIENT:'Enter a valid wallet address or Midori tag.',INVALID_REQUEST:'Check the payment details.',NOT_FOUND:'This tag was not found. Check the spelling.',TAG_RECONFIRM_REQUIRED:'This tag changed or expired. Choose the recipient again.',
    // Network cost is always paid in the same token being sent (self-pay),
    // so these messages name whichever asset is open, not a fixed token.
    INSUFFICIENT_FUNDS:'Not enough funds for the amount and network cost.',INSUFFICIENT_GAS_FUNDS:'Add {symbol} to cover the network cost.',GAS_TOKEN_UNSUPPORTED:'Network costs in {symbol} are temporarily unavailable. Try again later.',
    SPONSOR_BUDGET_EXHAUSTED:'Sponsorship is unavailable. Choose another payment mode and confirm again.',SPONSOR_UNAVAILABLE:'Sponsorship is unavailable. Choose another payment mode and confirm again.',
    GAS_LIMIT_EXCEEDED:'The network cost exceeds your limit. Request a new quote.',QUOTE_EXPIRED:'This quote expired. Review the payment again.',QUOTE_CHANGED:'The network cost changed. Review the payment again.',
    PAYMENT_CHANGED:'The payment details changed. Review and confirm again.',PAYMENT_CONFIGURATION_CHANGED:'The payment service changed. Review and confirm again.',PAYMENT_POLICY_REJECTED:'The payment details changed. Review and confirm again.',
    PAYMENTS_UNAVAILABLE:'Sending is temporarily unavailable. Your funds remain in your wallet.',PAYMENT_ALREADY_PENDING:'A payment is still being confirmed. Check its status before sending again.',
    IDEMPOTENCY_CONFLICT:'This payment was already reviewed with different details. Start a new review.',NETWORK_UNCONFIRMED:'The connection was interrupted. Check this payment before trying again.',
    PAYMENT_PROVIDER_LIMITED:'The payment service is busy. Try again shortly.',PAYMENT_PROVIDER_UNAVAILABLE:'The payment service is unavailable. Try again shortly.',QUOTE_UNAVAILABLE:'Could not calculate the network cost. No payment was sent.',
  };
  return t(messages[error?.code]||'Could not complete this step. Your payment has not been sent.', { symbol });
}
