import Taptic from './Taptic.js';
import { paymentLink } from '../../electron/lib/security.js';

export default async function deviceready() {
  await new Promise(resolve => document.addEventListener('deviceready', resolve, { once: true }));
  if (window.WebAssembly) WebAssembly.instantiateStreaming = false;
  const taptic = new Taptic();
  await taptic.init();
  window.taptic = taptic;
  document.addEventListener('backbutton', event => {
    event.preventDefault();
    if (window.backButtonModal) return window.backButtonModal();
    if (window.backButton) return window.backButton();
    window.navigator.app.exitApp();
  });
  if (window.cordova.InAppBrowser) window.open = value => {
    let url;
    try { url = new URL(value); } catch { return null; }
    if (!['https:', 'mailto:'].includes(url.protocol) || url.username || url.password) return null;
    return cordova.InAppBrowser.open(url.href, '_system');
  };
  window.handleOpenURL = value => {
    const link = paymentLink(value);
    if (link) window.navigateHandler?.(`/bip21/${encodeURIComponent(link)}`);
  };
  window.saveCryptosForExtensions = () => {};
  window.systemBars = { setStyle() {} };
}
