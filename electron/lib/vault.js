import { assertCaller } from './security.js';
import fs from 'node:fs/promises';
import { ipcMain, safeStorage, BrowserWindow, powerMonitor, dialog } from 'electron';
export function installVault() {
  const check = event => {
    assertCaller(event);
    if (!safeStorage.isEncryptionAvailable() || safeStorage.getSelectedStorageBackend?.() === 'basic_text') throw new Error('Unlock the operating system keyring to use protected storage.');
  };
  ipcMain.handle('vault:seal', (event, value) => { check(event); if (typeof value !== 'string' || value.length > 65536) throw new Error('Invalid vault data'); return safeStorage.encryptString(value).toString('base64'); });
  ipcMain.handle('vault:open', (event, value) => { check(event); if (typeof value !== 'string' || value.length > 100000) throw new Error('Invalid vault data'); return safeStorage.decryptString(Buffer.from(value, 'base64')); });
  const fileCaller = event => {
    assertCaller(event);
  };
  ipcMain.handle('recovery:save', async (event, value) => {
    fileCaller(event);
    if (typeof value !== 'string' || value.length > 32768 || JSON.parse(value).schema !== 'midori.safe-account') throw new Error('Invalid recovery descriptor');
    const selection = await dialog.showSaveDialog(BrowserWindow.fromWebContents(event.sender), { defaultPath: 'midori-recovery.json', filters: [{ name: 'Midori recovery', extensions: ['json'] }] });
    if (selection.canceled || !selection.filePath) throw new Error('Save cancelled');
    await fs.writeFile(selection.filePath, value, { encoding: 'utf8', mode: 0o600 });
  });
  ipcMain.handle('recovery:read', async event => {
    fileCaller(event);
    const selection = await dialog.showOpenDialog(BrowserWindow.fromWebContents(event.sender), { properties: ['openFile'], filters: [{ name: 'Midori recovery', extensions: ['json'] }] });
    if (selection.canceled || !selection.filePaths[0]) throw new Error('Open cancelled');
    if ((await fs.stat(selection.filePaths[0])).size > 32768) throw new Error('Invalid recovery descriptor');
    return fs.readFile(selection.filePaths[0], 'utf8');
  });
  const suspend = () => { for (const window of BrowserWindow.getAllWindows()) window.webContents.send('vault:suspend'); };
  powerMonitor.on('suspend', suspend); powerMonitor.on('lock-screen', suspend);
}
