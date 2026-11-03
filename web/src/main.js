import './runtime/buffer.js';
// polyfills
import 'core-js/es/string/replace-all';
import 'core-js/es/array/at';
import 'core-js/es/array/to-sorted';

import './assets/styles/app.scss';

import { addSentryVueIntegration } from './lib/sentry.js';

import App from './App.vue';
import { createAccount } from './lib/account.js';
import { createApp } from './lib/app.js';
import router from './router/router.js';
import i18n, { setLanguage } from './lib/i18n/i18n.js';

async function main() {
  let theme = 'system';
  try { const saved = localStorage.getItem('_cs_theme'); if (['system','light','dark'].includes(saved)) theme = saved; } catch { /* use the system theme */ }
  document.documentElement.dataset.theme = theme === 'system' ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light') : theme;
  window.electron?.setTheme(theme);
  document.addEventListener('touchstart', () => {}, false); // fix safari :active css bug
  if (import.meta.env.VITE_BUILD_TYPE === 'phonegap') {
    await (await import('../../phonegap/lib/deviceready.js')).default();
  }

  const app = createApp({ App, router });
  addSentryVueIntegration(app);
  await createAccount({ app, router });
  await setLanguage();
  app.use(i18n);
  app.use(router);
  app.mount('#app');
}

main().catch(error => {
  console.error('Midori Wallet startup failed');
  document.getElementById('app')?.remove();
  const fallback = document.getElementById('error');
  if (fallback) fallback.style.display = 'block';
});
