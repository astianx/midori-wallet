import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const playwright = await import(require.resolve('playwright-core'));
const { chromium } = playwright.default || playwright;
const args = process.argv.slice(2);
const value = (key, fallback) => args.includes(key) ? args[args.indexOf(key) + 1] : fallback;
const endpoint = value('--cdp', 'http://127.0.0.1:9224');
if (!['127.0.0.1', 'localhost'].includes(new URL(endpoint).hostname)) throw new Error('Use a local CDP endpoint');
const output = path.resolve(value('--out', '/tmp/midori-ui-audit'));
const scenarios = args.includes('--scenarios');
if (scenarios && !args.includes('--test-account')) throw new Error('Scenarios require an unlocked disposable test account and --test-account');
fs.mkdirSync(output, { recursive: true });
const browser = await chromium.connectOverCDP(endpoint);
const page = browser.contexts().flatMap(context => context.pages()).find(item => /localhost|127\.0\.0\.1/.test(item.url()));
if (!page) throw new Error('Open the local wallet first');
const report = { date: new Date().toISOString(), profile: 'revisión interna automatizada; no participantes', scenarios, checks: [], errors: [] };
page.on('pageerror', error => report.errors.push(error.message));
const go = async (name, theme = 'light', symbol) => {
  await page.evaluate(async ({ name, theme, symbol }) => {
    document.documentElement.dataset.theme = theme;
    const app = document.querySelector('#app').__vue_app__.config.globalProperties;
    const asset = app.$cryptos.find(item => !symbol || item.crypto.symbol === symbol);
    await app.$router.push({ name, params: name.startsWith('crypto') ? { cryptoId: asset.crypto._id } : {} });
  }, { name, theme, symbol });
  await page.waitForTimeout(400);
};
const capture = async name => {
  const check = await page.evaluate(() => ({ width: window.innerWidth, height: window.innerHeight, theme: document.documentElement.dataset.theme, route: document.querySelector('#app').__vue_app__.config.globalProperties.$route.name, horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth }));
  if (/auth|unlock|export/.test(check.route)) throw new Error('Audit stopped: unlock the test account; never capture recovery or export');
  await page.screenshot({ path: path.join(output, `${name}.png`) });
  report.checks.push({ name, ...check });
};
try {
  await page.bringToFront();
  const ready = await page.evaluate(() => { const app = document.querySelector('#app')?.__vue_app__?.config.globalProperties; return app && !app.$account.isLocked && app.$cryptos.length; });
  if (!ready) throw new Error('Unlock a local test wallet manually before running. This tool does not handle credentials.');
  if (!args.includes('--scenarios-only')) for (const width of [320, 375, 414, 768, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    for (const theme of ['light', 'dark']) {
      for (const route of ['home', 'activity', 'receive', 'crypto.receive', 'crypto.send', 'settings']) {
        await go(route, theme);
        await capture(`${route}-${width}-${theme}`);
      }
    }
  }
  if (scenarios) {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.evaluate(() => {
      const app = document.querySelector('#app').__vue_app__.config.globalProperties;
      window.__midoriAudit = { descriptor: Object.getOwnPropertyDescriptor(app, '$cryptos'), assets: app.$cryptos };
      const banner = document.createElement('div'); banner.id = 'midori-audit-banner'; banner.textContent = 'ESCENARIO UI · datos simulados · no es saldo ni operación real';
      banner.style.cssText = 'position:fixed;z-index:99999;top:0;left:0;right:0;padding:8px;text-align:center;background:var(--color-warning-surface);color:var(--color-warning);font:600 12px var(--font-body);pointer-events:none';
      document.body.append(banner);
    });
    for (const state of ['fresh', 'stale', 'unavailable']) {
      await go('receive');
      await page.evaluate(state => {
        const app = document.querySelector('#app').__vue_app__.config.globalProperties;
        Object.defineProperty(app, '$cryptos', { configurable: true, get: () => window.__midoriAudit.assets.map((item, index) => ({ ...item, readState: state, balance: state === 'unavailable' ? undefined : { value: index ? 125000000n : 250125600n, toString: () => index ? '125' : '250.1256' } })) });
      }, state);
      await go('home');
      await capture(`scenario-balance-${state}`);
    }
    await page.evaluate(() => { Object.defineProperty(document.querySelector('#app').__vue_app__.config.globalProperties, '$cryptos', window.__midoriAudit.descriptor); });
    for (const state of ['payments-orders', 'partial-error', 'disconnected', 'empty']) {
      await go('receive'); await go('activity');
      await page.evaluate(state => {
        const app = document.querySelector('#app').__vue_app__;
        const find = node => { if (!node) return null; if (node.component?.type?.__name === 'MwActivityList' || node.component?.type?.name === 'MwActivityList') return node.component.proxy; const candidates = [node.component?.subTree, ...(Array.isArray(node.children) ? node.children : [])]; for (const child of candidates) { const result = find(child); if (result) return result; } return null; };
        const vm = find(app._instance.subTree);
        if (!vm) throw new Error('Activity component not found');
        vm.disposed = true; clearInterval(vm.timer); vm.loading = false;
        vm.sources.forEach(source => { source.records = []; source.hasMore = false; source.error = ['partial-error', 'disconnected'].includes(state); });
        vm.orders = [];
        if (state === 'disconnected' || state === 'empty') return;
        const now = new Date();
        vm.sources[0].records = [{ id: 'ui-example', reference: 'UI-ONLY-EXAMPLE', source: 'payment', symbol: 'USDC', incoming: true, timestamp: now, label: 'Recibido · ejemplo', amount: '+25.1256 USDC', state: 'confirmed' }, { id: 'ui-pending', reference: 'UI-ONLY-PENDING', source: 'payment', symbol: 'USDT', incoming: false, timestamp: now, label: 'Enviado · ejemplo', amount: '−5 USDT', state: 'pending' }];
        vm.orders = [{ id: 'ui-order', reference: 'UI-ONLY-ORDER', source: 'ramp', symbol: 'USDC', timestamp: now, label: 'Agregar dinero · ejemplo', amount: '20 USD', state: 'processing', environment: 'STAGING' }];
      }, state);
      await capture(`scenario-activity-${state}`);
    }
    await go('crypto.send');
    await capture('scenario-send-entry');
    await go('crypto.receive');
    await capture('scenario-receive-external');
    await go('home');
    await page.evaluate(() => { const button = [...document.querySelectorAll('.mw-balance-card button')].find(item => item.textContent.includes('Enviar') || item.textContent.includes('Send')); if (button) button.disabled = true; });
    await capture('scenario-send-disabled-presentation');
  }
} finally {
  await page.evaluate(() => {
    const app = document.querySelector('#app')?.__vue_app__?.config.globalProperties;
    if (app && window.__midoriAudit) Object.defineProperty(app, '$cryptos', window.__midoriAudit.descriptor);
    document.querySelector('#midori-audit-banner')?.remove(); delete window.__midoriAudit;
  }).catch(() => {});
  await go('receive').then(() => go('home')).catch(() => {});
  fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  await browser.close();
}
console.log(JSON.stringify({ output, captures: report.checks.length, errors: report.errors.length }));
