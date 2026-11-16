<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwPin from '../../components/MwPin.vue';
import MwStep from '../../components/MwStep.vue';

import { redirectToApp } from '../../lib/mixins.js';

export default {
  inject: ['setupBiometry'],
  components: {
    AuthStepLayout,
    MwPin,
  },
  extends: MwStep,
  mixins: [redirectToApp],
  data() { return { busy: false }; },
  methods: {
    async setup(pin) {
      this.busy = true;
      try { await this.$account.createFromRecovery(this.storage.recoveryHandle, pin); } finally { this.busy = false; }

      if (this.$account.biometry.isAvailable) {
        this.setupBiometry.prepare(pin);
        this.next('biometry');
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
    :title="$t('Set a PIN')"
    :showBack="!busy" :description="$t('for quick access')" :stepNumber="storage.flow === 'recover' ? 2 : 3" :stepCount="storage.flow === 'recover' ? 2 : 3"
  >
    <MwPin
      mode="setup"
      @success="setup"
    />
  </AuthStepLayout>
</template>
