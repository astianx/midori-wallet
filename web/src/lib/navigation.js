let returnPath;
const paymentDrafts = new Map();

export function rememberDestination(route) {
  if (route.meta.requiresAuth) returnPath = route.fullPath;
}

export function consumeDestination() {
  const path = returnPath;
  returnPath = undefined;
  return path;
}

export function savePaymentDraft(owner, path, draft) {
  const key = `${owner}:${path}`;
  paymentDrafts.delete(key);
  paymentDrafts.set(key, { address: draft.address, amount: draft.amount });
  if (paymentDrafts.size > 8) paymentDrafts.delete(paymentDrafts.keys().next().value);
}

export function takePaymentDraft(owner, path) {
  const key = `${owner}:${path}`;
  const draft = paymentDrafts.get(key);
  paymentDrafts.delete(key);
  return draft;
}

export function clearNavigationDrafts() {
  returnPath = undefined;
  paymentDrafts.clear();
}
