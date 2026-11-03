// eslint-disable-next-line no-undef
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electron', {
  registerNavigateHandler: (callback) => ipcRenderer.on('navigate', (event, path) => callback(path)),
  arch: process.arch,
  recoveryFiles: { save: text => ipcRenderer.invoke('recovery:save', text), read: () => ipcRenderer.invoke('recovery:read') },
  vault: { seal: value => ipcRenderer.invoke('vault:seal', value), open: value => ipcRenderer.invoke('vault:open', value) },
  onSuspend(callback) {
    const handler = () => callback();
    ipcRenderer.on('vault:suspend', handler);
    return () => ipcRenderer.removeListener('vault:suspend', handler);
  },
  setTheme(theme) {
    ipcRenderer.invoke('setTheme', theme);
  },
});
