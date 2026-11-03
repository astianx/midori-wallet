<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwPin from '../../components/MwPin.vue';
import MwStep from '../../components/MwStep.vue';

import { redirectToApp } from '../../lib/mixins.js';

export default {
  components: {
    AuthStepLayout,
    MwPin,
  },
  extends: MwStep,
  mixins: [redirectToApp],
  methods: {
    async setup(pin) {
      await this.$account.createFromRecovery(this.storage.recoveryHandle, pin);

      if (this.$account.biometry.isAvailable) {
        this.updateStorage({ pin });
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
    :description="$t('for quick access')"
  >
    <MwPin
      mode="setup"
      @success="setup"
    />
  </AuthStepLayout>
</template>
