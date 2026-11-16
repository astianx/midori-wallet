export const fileBase64 = file => new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result.split(',')[1]); reader.onerror = reject; reader.readAsDataURL(file); });
export async function deliverReceiptFile(file, runtime, share = true) {
  if (!file || file.type !== 'application/pdf' || file.size > 8 * 1024 * 1024) throw Error('Invalid receipt file');
  if (runtime === 'electron' && window.electron?.receiptFiles) return window.electron.receiptFiles.save({ name: file.name, type: file.type, data: await fileBase64(file) });
  if (runtime === 'phonegap' && window.cordova?.exec) {
    const data = await fileBase64(file);
    return new Promise((resolve, reject) => window.cordova.exec(resolve, reject, 'MidoriVault', share ? 'shareReceipt' : 'saveReceipt', [file.name, file.type, data]));
  }
  if (share && navigator.share && navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], title: 'Midori Wallet' }); return { shared: true }; }
    catch (error) { if (error.name === 'AbortError') return { cancelled: true }; throw error; }
  }
  const url = URL.createObjectURL(file), anchor = document.createElement('a'); anchor.href = url; anchor.download = file.name; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 60000); return { saved: true };
}
