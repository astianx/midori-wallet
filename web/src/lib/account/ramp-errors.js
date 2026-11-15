export function rampError(error,t) {
  const messages={
    INVALID_REQUEST:'Check the amount and currency.',NOT_FOUND:'This order was not found.',
    RAMP_UNAVAILABLE:'The payment service is unavailable. Try again shortly.',
    RAMP_PROVIDER_UNAVAILABLE:'The payment service is unavailable. Try again shortly.',
    IDEMPOTENCY_CONFLICT:'This order was already reviewed with different details. Start again.',
    NETWORK_UNCONFIRMED:'The connection was interrupted. Check this order before trying again.',
  };
  return t(messages[error?.code]||'Could not start this order.');
}
