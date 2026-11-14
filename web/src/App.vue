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
  data() { return { checkout: null }; },
  mounted() {
    eventBus.on('openRampCheckout', this.openCheckout);
    eventBus.on('discardRampCheckout', this.discardCheckout);
    if (this.env.VITE_BUILD_TYPE === 'phonegap') {
      window.navigator.splashscreen?.hide();
    }
  },
  beforeUnmount() {
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
    },
    checkoutError() {
      const order = this.checkout;
      this.discardCheckout();
      eventBus.emit('rampCheckoutError', order);
    },
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
