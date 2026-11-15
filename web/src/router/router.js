import { rememberDestination, consumeDestination, clearNavigationDrafts } from '../lib/navigation.js';
import * as state from '../lib/state.js';
import { WalletState } from '../lib/account/domain.js';
import {
  createRouter,
  createWebHashHistory,
  createWebHistory,
} from 'vue-router';

import appRoutes from './routes/app.js';
import authRoutes from './routes/auth.js';
import designRoutes from './routes/design.js';

import {
  defineAppProperty,
  registerProtocolHandler,
} from '../lib/helpers.js';

const router = createRouter({
  history: import.meta.env.VITE_BUILD_TYPE === 'web' ?
    createWebHistory(import.meta.env.BASE_URL) : createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    ...appRoutes,
    ...authRoutes,
    ...import.meta.env.DEV ? designRoutes : [],
  ],
});

router.up = async () => {
  const route = router.currentRoute.value;
  const previous = router.options.history.state.back;
  if (typeof previous === 'string' && previous.startsWith('/') && !previous.startsWith('//') && previous !== route.fullPath && router.resolve(previous).meta.requiresAuth) {
    router.back();
    return;
  }
  if (route.path === '/') {
    if (import.meta.env.VITE_BUILD_TYPE === 'phonegap') window.navigator.app.exitApp();
    return;
  }
  const parent = route.path.replace(/(\/[^/]*)$/, '') || '/';
  await router.replace(parent);
};

router.rememberDestination = () => rememberDestination(router.currentRoute.value);
router.resumeDestination = () => router.replace(consumeDestination() || { name: 'home' });
router.navigateExternal = path => {
  clearNavigationDrafts();
  return router.push({ path, force: true });
};

router.beforeEach((to, from) => {
  if (import.meta.env.DEV && to.meta.dev) return;
  const { $account, $app } = router;
  if (to.meta.requiresAuth && !$account.isCreated) {
    rememberDestination(to);
    return { name: 'auth', replace: true };
  }
  if (to.meta.requiresAuth && $account.isLocked) {
    rememberDestination(to);
    return { name: 'unlock', replace: true };
  }
  if (to.meta.crypto) {
    const wallet = $account.wallet(to.params.cryptoId);
    if (wallet) {
      if ($app.config.globalProperties.$wallet !== wallet) setWalletProps($app, wallet);
      registerProtocolHandler(wallet.crypto, $account);
    } else {
      const crypto = $account.cryptoDB.get(to.params.cryptoId);
      if (crypto?.supported && !crypto?.deprecated) {
        return { name: 'crypto.add', replace: true, query: { cryptoId: to.params.cryptoId } };
      } else {
        return { name: 'home', replace: true };
      }
    }
  }
  if (to.meta.requiresCards && !$account.cards.isEnabled) {
    return { name: 'home', replace: true };
  }
  const toDepth = to.path.split('/').length;
  const fromDepth = from.path.split('/').length;
  if (toDepth !== fromDepth) {
    to.meta.transition = toDepth > fromDepth ? 'slide-left' : 'slide-right';
  }
  if (to.name === from.name) {
    to.meta.viewRevision = Date.now();
  }
});

router.afterEach((to) => {
  const { $account, $app } = router;
  if (to.meta.crypto && $account.wallet(to.params.cryptoId)) {
    return;
  }
  unsetWalletProps($app);
});

if (import.meta.env.VITE_BUILD_TYPE === 'electron') {
  window.electron?.registerNavigateHandler(router.navigateExternal);
}

if (import.meta.env.VITE_BUILD_TYPE === 'phonegap' ||
  (import.meta.env.VITE_BUILD_TYPE === 'web' && import.meta.env.DEV)) {
  window.navigateHandler = router.navigateExternal;
}

function setWalletProps($app, wallet) {
  const { $account } = router;
  defineAppProperty($app, '$wallet', wallet);
  state.walletState.value = wallet.state;

  const $loadWallet = async () => {
    if (state.walletState.value === WalletState.STATE_LOADING) return;
    state.walletState.value = WalletState.STATE_LOADING;
    try {
      await wallet.cleanup();
      await wallet.load();
      $account.emit('update');
    } catch (err) {
      console.error('Midori Wallet operation failed');
    }
    state.walletState.value = $app.config.globalProperties.$wallet?.state;
  };
  defineAppProperty($app, '$loadWallet', $loadWallet);

  if (wallet.state !== WalletState.STATE_LOADED) {
    $loadWallet();
  }
}

function unsetWalletProps($app) {
  defineAppProperty($app, '$wallet', undefined);
  defineAppProperty($app, '$loadWallet', undefined);
  state.walletState.value = undefined;
}

export default router;
