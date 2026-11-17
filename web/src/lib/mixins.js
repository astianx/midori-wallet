import { captureWalletError } from './sentry.js';
const shownComponents = new WeakSet();
const show = component => {
  if (shownComponents.has(component)) return;
  shownComponents.add(component);
  component.$options.onShow?.call(component);
};
const hide = component => {
  if (!shownComponents.delete(component)) return;
  component.$options.onHide?.call(component);
};
export const onShowOnHide = {
  mounted() { show(this); },
  activated() { show(this); },
  beforeUnmount() { hide(this); },
  deactivated() { hide(this); },
};

export const walletSeed = {
  methods: {
    async walletSeed(callback, { step = 'pin', keepStep = false, layout = 'MainLayout' } = {}) {
      try {
        if (this.$account.settings.get('1faWallet')) {
          this.next(step, {
            layout,
            mode: 'walletSeed',
            success: async (walletSeed) => {
              await callback(walletSeed);
              if (keepStep) this.back();
            },
          });
        } else {
          const walletSeed = await this.$account.getNormalSecurityWalletSeed();
          if (!walletSeed) return;
          await callback(walletSeed);
        }
      } catch (err) {
        captureWalletError(err, 'mixins.operation');
      }
    },
  },
};

export const redirectToApp = {
  methods: {
    redirectToApp() {
      this.$router.resumeDestination();
    },
  },
};
