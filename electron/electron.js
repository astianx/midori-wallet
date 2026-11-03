import { fileURLToPath, pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';
import log from 'electron-log';
import { Menu, app, ipcMain, nativeTheme, net, protocol } from 'electron';

import menu from './lib/menu.js';
import openWindow from './lib/openWindow.js';
import schemes from './dist/schemes.js';
import updater from './lib/updater.js';
import { installVault } from './lib/vault.js';
import { assertCaller, paymentLink } from './lib/security.js';
import path from 'node:path';
import {
  APP_HOSTNAME,
  MIDORI_BUILD_PROFILE,
  VITE_SITE_URL,
  isDevelopment,
  isLinux,
  isMac,
  isMas,
  isWindows,
} from './lib/constants.js';

app.setName(MIDORI_BUILD_PROFILE === 'release' ? 'Midori Wallet' : 'Midori Wallet Beta');
app.setPath('userData', path.join(app.getPath('appData'), MIDORI_BUILD_PROFILE === 'release' ? 'midori-wallet' : 'midori-wallet-beta'));

log.initialize({ spyRendererConsole: false });
if (!isDevelopment) {
  log.transports.file.level = false;
}


const installerEvent = process.argv[1];
if (isWindows && app.isPackaged && ['--squirrel-install','--squirrel-updated','--squirrel-uninstall','--squirrel-obsolete'].includes(installerEvent)) {
  if (installerEvent !== '--squirrel-obsolete') {
    const update = path.resolve(path.dirname(process.execPath), '..', 'Update.exe');
    const action = installerEvent === '--squirrel-uninstall' ? '--removeShortcut' : '--createShortcut';
    try { execFileSync(update, [action, path.basename(process.execPath)], { windowsHide: true, timeout: 15000, stdio: 'ignore' }); }
    catch { log.warn('Installer shortcut update failed'); }
  }
  app.exit(0);
}

if (isWindows) {
  app.setAppUserModelId(`com.squirrel.${MIDORI_BUILD_PROFILE === 'release' ? 'midori_wallet.midori-wallet' : 'midori_wallet_beta.midori-wallet-beta'}`);
  app.setAboutPanelOptions({
    iconPath: fileURLToPath(new URL('./resources/64x64.png', import.meta.url)),
  });
}

if (isLinux) {
  app.setAboutPanelOptions({
    applicationName: app.name,
    applicationVersion: app.getVersion(),
    website: VITE_SITE_URL,
    iconPath: fileURLToPath(new URL('./resources/64x64.png', import.meta.url)),
  });
}

let startupUrl;
const lock = app.requestSingleInstanceLock();

// https://github.com/electron/electron/issues/15958
if (!isMas && !lock) {
  app.quit();
  //return;
}



// Set up Application Menu
Menu.setApplicationMenu(menu);

(MIDORI_BUILD_PROFILE === 'release' ? [...schemes, { scheme: 'midoriwallet' }] : [{ scheme: 'midoriwallet-beta' }]).forEach((item) => {
  if (!app.isDefaultProtocolClient(item.scheme)) {
    // Define custom protocol handler.
    // Deep linking works on packaged versions of the application!
    app.setAsDefaultProtocolClient(item.scheme);
  }
});

// The application has finished basic startup
app.on('will-finish-launching', () => {
  // Protocol handler for macOS
  app.on('open-url', (event, url) => {
    event.preventDefault();
    if (app.isReady()) {
      openWindow(url);
    } else {
      startupUrl = paymentLink(url);
    }
  });
});

function extractUrlFromArgv(argv) {
  return argv.find(arg => paymentLink(arg));
}

function shouldHandlePathname(pathname) {
  if (pathname.startsWith('/api/')) return false;
  return true;
}

// Someone tried to run a second instance
app.on('second-instance', (event, argv) => {
  openWindow(extractUrlFromArgv(argv));
});

// Quit when all windows are closed.
app.on('window-all-closed', () => {
  // On macOS it is common for applications and their menu bar
  // to stay active until the user quits explicitly with Cmd + Q
  if (!isMac) {
    app.quit();
  }
});

// This method will be called when Electron has finished
// initialization and is ready to create browser windows.
// Some APIs can only be used after this event occurs.
app.whenReady().then(() => {
  log.info('app ready');
  installVault();


  ipcMain.handle('setTheme', (event, theme) => {
    if (!['system', 'light', 'dark'].includes(theme)) {
      throw new Error(`Invalid theme source: ${theme}`);
    }
    assertCaller(event);
    nativeTheme.themeSource = theme;
  });

  // handle local files
  protocol.handle('https', (req) => {
    const { host, pathname } = new URL(req.url);
    if (host === APP_HOSTNAME) {
      if (!shouldHandlePathname(pathname)) return new Response('', { status: 404 });
      const base = fileURLToPath(new URL('./dist/', import.meta.url));
      let decoded;
      try { decoded = decodeURIComponent(pathname); } catch { return new Response('', { status: 400 }); }
      const resource = decoded === '/' ? '/index.html' : decoded;
      const file = path.resolve(base, `.${resource}`);
      if (!file.startsWith(base) || decoded.includes('\\') || !/^\/(?:index\.html|(?:variables|schemes)\.js|build-info\.json|THIRD-PARTY-NOTICES\.txt|assets\/|favicon|apple-touch|site\.webmanifest|safari-pinned|mstile|android-chrome)/.test(resource)) return new Response('', { status: 404 });
      return net.fetch(pathToFileURL(file).href);
    }
    return net.fetch(req, { bypassCustomProtocolHandlers: true });
  });

  if (isMac) {
    openWindow(startupUrl);
  } else {
    openWindow(extractUrlFromArgv(process.argv));
  }

  updater.launchCheckForUpdates();

  app.on('activate', () => {
    // On macOS it's common to re-create a window in the app when the
    // dock icon is clicked and there are no other windows open.
    openWindow();
  });
});
