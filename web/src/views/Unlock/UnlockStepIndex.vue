<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwPin from '../../components/MwPin.vue';
import MwStep from '../../components/MwStep.vue';
import { onShowOnHide, redirectToApp } from '../../lib/mixins.js';

export default {
  components: {
    AuthStepLayout,
    MwPin,
  },
  extends: MwStep,
  mixins: [onShowOnHide, redirectToApp],
  data() {
    return {
      isReady: this.env.VITE_PLATFORM !== 'android',
    };
  },
  onShow() {
    if (this.env.VITE_PLATFORM === 'android') {
      setTimeout(() => {
        this.isReady = true;
        window.systemBars.setStyle(this.$resolvedTheme === 'light' ? 'light' : 'dark');
      }, 500);
    }
  },
  onHide() {
    if (this.$refs.pin) this.$refs.pin.value = '';
  },
  methods: {
    async success() {
      this.done();
    },
    done() {
      if (this.$account.walletsNeedSynchronization.length) {
        this.next('synchronization');
      } else if (this.$account.cryptosToSelect) {
        this.next('selectCryptos');
      } else {
        this.redirectToApp();
      }
    },
  },
};
</script>

<template>
  <AuthStepLayout
    :title="$t('Enter your PIN')"
    :showBack="false"
    :centered="true" :description="$t('for quick access')"
  >
    <MwPin
      v-if="isReady"
      ref="pin"
      mode="deviceSeed"
      logoutButton
      @success="success"
    />
    <details><summary>{{ $t('Forgot your PIN?') }}</summary><p>{{ $t('Your 12 words restore your wallet. You can also save this optional recovery file. It contains no private keys.') }}</p><p>{{ $t('Log out only if you have your recovery phrase.') }}</p></details>
  </AuthStepLayout>
</template>
