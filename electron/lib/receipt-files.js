import { BrowserWindow, dialog, ipcMain } from 'electron';
import fs from 'node:fs/promises';
import { assertCaller } from './security.js';
export function installReceiptFiles() {
  ipcMain.handle('receipt:save', async (event, value) => {
    assertCaller(event);
    if (!value || Object.keys(value).sort().join(',') !== 'data,name,type' || !/^midori-receipt-[0-9a-f-]{36}\.(png|pdf)$/.test(value.name) || !['image/png','application/pdf'].includes(value.type) || typeof value.data !== 'string' || value.data.length > 12 * 1024 * 1024 || !/^[A-Za-z0-9+/]*={0,2}$/.test(value.data)) throw Error('Invalid receipt file');
    const bytes = Buffer.from(value.data, 'base64'), pdf = value.type === 'application/pdf';
    if (bytes.length > 8 * 1024 * 1024 || pdf && (!value.name.endsWith('.pdf') || bytes.subarray(0,5).toString() !== '%PDF-') || !pdf && (!value.name.endsWith('.png') || bytes.subarray(0,8).toString('hex') !== '89504e470d0a1a0a')) throw Error('Invalid receipt file');
    const selection = await dialog.showSaveDialog(BrowserWindow.fromWebContents(event.sender), { defaultPath: value.name, filters: [{ name: pdf ? 'PDF' : 'PNG', extensions: [pdf ? 'pdf' : 'png'] }] });
    if (selection.canceled || !selection.filePath) return { cancelled: true };
    await fs.writeFile(selection.filePath, bytes, { mode: 0o600 }); return { saved: true };
  });
}
