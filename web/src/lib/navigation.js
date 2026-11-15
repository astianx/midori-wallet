let returnPath;
let paymentDraft;

export function rememberDestination(route) {
  if (route.meta.requiresAuth) returnPath = route.fullPath;
}

export function consumeDestination() {
  const path = returnPath;
  returnPath = undefined;
  return path;
}

export function savePaymentDraft(owner, path, draft) {
  paymentDraft = { owner, path, address: draft.address, amount: draft.amount };
}

export function takePaymentDraft(owner, path) {
  if (paymentDraft?.owner !== owner || paymentDraft.path !== path) return;
  const draft = paymentDraft;
  paymentDraft = undefined;
  return draft;
}

export function clearNavigationDrafts() {
  returnPath = undefined;
  paymentDraft = undefined;
}
