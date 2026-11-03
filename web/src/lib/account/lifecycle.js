import { clearRecovery } from './recovery.js';
export function installAccountLifecycle({ account, lock, document: doc = document, window: win = window }) {
  const suspend = () => { if (!account.isLocked || account.isOpening) { clearRecovery(); lock(); } };
  const visibility = () => { if (doc.hidden) suspend(); };
  doc.addEventListener('visibilitychange', visibility);
  doc.addEventListener('pause', suspend);
  win.addEventListener('pagehide', suspend);
  const removeNative = win.electron?.onSuspend?.(suspend);
  return () => { doc.removeEventListener('visibilitychange', visibility); doc.removeEventListener('pause', suspend); win.removeEventListener('pagehide', suspend); removeNative?.(); };
}
