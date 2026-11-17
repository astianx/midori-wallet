export function exchangeError(error, t) {
  return t({
    EXCHANGE_UNAVAILABLE: 'Crypto conversion is temporarily unavailable.',
    EXCHANGE_PROVIDER_UNAVAILABLE: 'Changelly is unavailable. Refresh and check your order before trying again.',
    EXCHANGE_PAIR_UNAVAILABLE: 'This currency pair or amount is unavailable.',
    EXCHANGE_AMOUNT_UNAVAILABLE: 'The amount does not cover the payout network cost.',
    EXCHANGE_QUOTE_EXPIRED: 'This quote expired. Review the conversion again.',
    IDEMPOTENCY_CONFLICT: 'Review your recent conversions before starting another.',
    PAYMENTS_UNAVAILABLE: 'Sending is temporarily unavailable. Your funds remain in your wallet.',
    INVALID_REQUEST: 'Check the amount, destination address and network.',
  }[error?.code] || 'Could not load the conversion. Refresh to check its status.');
}
