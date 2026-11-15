<script>
import MwErrorHardwareNotSupported from './components/MwErrorHardwareNotSupported.vue';
import MwModalUseHardwareKey from './components/MwModalUseHardwareKey.vue';
import MwUpdater from './components/MwUpdater.vue';
import CryptoRampCheckout from './views/Crypto/CryptoRampCheckout.vue';
import eventBus from './lib/eventBus.js';
import { RouterView } from 'vue-router';

export default {
  components: {
    RouterView,
    MwErrorHardwareNotSupported,
    MwModalUseHardwareKey,
    MwUpdater,
    CryptoRampCheckout,
  },
  data() { return { checkout: null, checkoutGuard: null }; },
  watch: { '$route.name'(name) { if (name === 'unlock') this.discardCheckout(); } },
  mounted() {
    this.checkoutGuard = (to, from) => {
      if (!this.checkout) return false;
      if (!to.meta.requiresAuth || to.name === 'unlock') { this.discardCheckout(); return false; }
      if (to.fullPath !== from.fullPath) { this.closeCheckout(); return true; }
      return false;
    };
    this.$router.closeRampCheckout = this.checkoutGuard;
    eventBus.on('openRampCheckout', this.openCheckout);
    eventBus.on('discardRampCheckout', this.discardCheckout);
    if (this.env.VITE_BUILD_TYPE === 'phonegap') {
      window.navigator.splashscreen?.hide();
    }
  },
  beforeUnmount() {
    if (this.$router.closeRampCheckout === this.checkoutGuard) delete this.$router.closeRampCheckout;
    eventBus.off('openRampCheckout', this.openCheckout);
    eventBus.off('discardRampCheckout', this.discardCheckout);
  },
  methods: {
    openCheckout(order) { this.checkout = order; },
    discardCheckout() { this.checkout = null; },
    updateCheckout() { eventBus.emit('rampCheckoutUpdate', this.checkout); },
    closeCheckout() {
      const order = this.checkout;
      this.discardCheckout();
      eventBus.emit('rampCheckoutUpdate', order);
      eventBus.emit('rampCheckoutClosed', order);
    },
    checkoutError(reason) { eventBus.emit('rampCheckoutError', { ...this.checkout, checkoutError: reason }); },
  },
};
</script>

<template>
  <RouterView />
  <CryptoRampCheckout
    v-if="checkout"
    :key="checkout.widgetUrl"
    :order="checkout"
    :title="$t(checkout.kind === 'buy' ? 'Continue to buy' : 'Continue to sell')"
    @close="closeCheckout"
    @update="updateCheckout"
    @error="checkoutError"
  />
  <MwUpdater />
  <MwErrorHardwareNotSupported />
  <MwModalUseHardwareKey />
</template>

<style lang="scss">
  #app {
    width: 100%;
    height: 100%;
  }
</style>
