import { fileURLToPath } from 'node:url';
import { BrowserWindow, shell } from 'electron';
import { isDevelopment } from './constants.js';
import { APP_ORIGIN, isAppUrl, externalUrl, paymentLink } from './security.js';

export default function openWindow(value) {
  const deeplink = paymentLink(value);
  const path = deeplink ? `/bip21/${encodeURIComponent(deeplink)}` : '/';
  let mainWindow = BrowserWindow.getAllWindows()[0];
  if (!mainWindow) {
    mainWindow = new BrowserWindow({
      width: 500, height: 700, minWidth: 360, minHeight: 500,
      webPreferences: { devTools: isDevelopment, sandbox: true, nodeIntegration: false, contextIsolation: true, webSecurity: true, webviewTag: false, preload: fileURLToPath(new URL('preload.cjs', import.meta.url)) },
    });
    mainWindow.webContents.on('will-navigate', (event, url) => { if (!isAppUrl(url)) event.preventDefault(); });
    mainWindow.webContents.on('will-redirect', (event, url) => { if (!isAppUrl(url)) event.preventDefault(); });
    mainWindow.webContents.on('will-attach-webview', event => event.preventDefault());
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
      const allowed = externalUrl(url);
      if (allowed) shell.openExternal(allowed).catch(() => {});
      return { action: 'deny' };
    });
    const session = mainWindow.webContents.session;
    session.setPermissionRequestHandler((contents, permission, callback, details) => callback(permission === 'media' && contents === mainWindow.webContents && isAppUrl(details.requestingUrl) && details.mediaTypes?.length === 1 && details.mediaTypes[0] === 'video'));
    session.setPermissionCheckHandler((contents, permission, origin, details) => contents === mainWindow.webContents && permission === 'media' && isAppUrl(origin) && details.mediaType === 'video');
    mainWindow.loadURL(`${APP_ORIGIN}/#${path}`);
  } else {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    if (deeplink) mainWindow.webContents.send('navigate', path);
  }
  return mainWindow;
}
