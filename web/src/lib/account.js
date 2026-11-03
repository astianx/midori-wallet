import * as state from './state.js';
import Account from './account/Account.js';
import { installAccountLifecycle } from './account/lifecycle.js';
import { Amount } from './account/domain.js';
import { release } from './version.js';
import { setLanguage } from './i18n/i18n.js';
import { cryptoSubtitle, cryptoToFiat, defineAppProperty, roundCrypto } from './helpers.js';
import { setSentryConnection, setSentryUser } from './sentry.js';

export async function createAccount({ app, router }) {
  const account = new Account({
    localStorage: window.localStorage,
    release,
  });
  await account.biometry.init();
  const removeLifecycle = installAccountLifecycle({ account, lock: () => account.lock() });

  defineAppProperty(app, '$account', account);
  state.isOnion.value = account.isOnion;
  router.$account = account;

  const dummyBalances = {
    'bitcoin@bitcoin': '0.5',
    'ethereum@ethereum': '0.5',
    'tether@ethereum': '100',
    'xrp@ripple': '2300',
    'monero@monero': '69',
    'dogecoin@dogecoin': '400',
    'cardano@cardano': '220',
    'litecoin@litecoin': '50',
    'dash@dash': '1',
    'toncoin@toncoin': '900',
  };

  account.on('update', async (context) => {
    if (account.isLocked) return;
    switch (context) {
      case 'currency':
        state.currency.value = account.details.get('systemInfo').preferredCurrency;
        break;
      case 'language':
        await setLanguage(account.details.get('systemInfo').language);
        break;
      case 'user':
        state.user.value = account.user;
        break;
      case 'isHiddenBalance':
        state.isHiddenBalance.value = account.isHiddenBalance;
        break;
      case 'theme':
        state.resolvedTheme.value = account.applyTheme();
        break;
      case 'isOnion':
        state.isOnion.value = account.isOnion;
        setSentryConnection();
        break;
      default: {
        const result = [];
        for (const wallet of account.wallets()) {
          const market = await account.market.getMarket(wallet.crypto._id, state.currency.value);
          if (account.isDummy && dummyBalances[wallet.crypto._id]) {
            Object.defineProperty(wallet, 'balance', {
              get() {
                return Amount.fromString(dummyBalances[wallet.crypto._id], wallet.crypto.decimals);
              },
              enumerable: true,
              configurable: true,
            });
          }
          result.push({
            crypto: wallet.crypto,
            platform: wallet.platform,
            balance: wallet.balance,
            balanceRound: wallet.balance && market?.price ? roundCrypto(wallet.balance, market.price) : wallet.balance,
            readState: wallet.readState,
            readAt: wallet.readAt,
            balanceFiat: wallet.balance && market?.price ? Number(cryptoToFiat(wallet.balance, market.price)) : undefined,
            rank: wallet.crypto.meta?.rank || Infinity,
            title: wallet.crypto.symbol,
            subtitle: cryptoSubtitle(wallet),
            market,
          });
        }
        result.sort((a, b) => {
          if (a.balanceFiat > b.balanceFiat) return -1;
          if (a.balanceFiat < b.balanceFiat) return 1;
          if ((a.balance?.value ?? -1n) > (b.balance?.value ?? -1n)) return -1;
          if ((a.balance?.value ?? -1n) < (b.balance?.value ?? -1n)) return 1;
          if (a.rank < b.rank) return -1;
          if (a.rank > b.rank) return 1;
          if (a.crypto.original && !b.crypto.original) return -1;
          if (!a.crypto.original && b.crypto.original) return 1;
          return a.crypto.symbol.localeCompare(b.crypto.symbol);
        });
        if (account.isLocked) return;
        state.cryptos.value = result;
        state.walletState.value = app.config.globalProperties.$wallet?.state;

        if (import.meta.env.VITE_PLATFORM === 'ios') {
          window.saveCryptosForExtensions(result);
        }
        setSentryUser();
      }
    }
  });
  account.on('locked', () => { state.reset(); router.replace({ name: 'unlock' }); });
  account.on('logout', async () => {
    removeLifecycle();
    if (import.meta.env.VITE_PLATFORM === 'ios') {
      window.saveCryptosForExtensions();
    }
    setSentryUser();
    state.reset();
    await createAccount({ app, router });
  });
}
