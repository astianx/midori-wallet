export function selectReceivingText(element) {
  if (!element) return false;
  element.focus({ preventScroll: true });
  if (element.matches('textarea,input')) element.select();
  else { const range = document.createRange(); range.selectNodeContents(element); const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range); }
  return true;
}

export async function shareReceivingText(text, title, runtime) {
  const plugin = runtime === 'phonegap' && window.plugins?.socialsharing;
  if (typeof plugin?.shareWithOptions === 'function') {
    await new Promise((resolve, reject) => plugin.shareWithOptions({ message: text, subject: title }, resolve, reject));
    return 'closed';
  }
  if (typeof navigator.share !== 'function' || navigator.canShare && !navigator.canShare({ text, title })) return 'fallback';
  try { await navigator.share({ text, title }); return 'closed'; }
  catch (error) { if (error?.name === 'AbortError') return 'cancelled'; throw error; }
}
