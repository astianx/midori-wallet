<script>
import MwSteps from '../../components/MwSteps.vue';

import AuthStepBiometry from './AuthStepBiometry.vue';
import AuthStepIndex from './AuthStepIndex.vue';
import AuthStepLogin from './AuthStepLogin.vue';
import AuthStepPassphrase from './AuthStepPassphrase.vue';
import AuthStepPassphraseConfirmation from './AuthStepPassphraseConfirmation.vue';
import AuthStepPassphraseGeneration from './AuthStepPassphraseGeneration.vue';
import AuthStepPin from './AuthStepPin.vue';
import { clearRecovery } from '../../lib/account/recovery.js';
import MwSelectCryptosStep from '../../components/MwSelectCryptosStep.vue';

const handoffs = new WeakMap();
export default {
  components: {
    MwSteps,
  },
  provide() { let pin = ''; const context = { prepare: value => { pin = value; }, enable: () => this.$account.biometry.enable(pin), clear: () => { pin = ''; } }; handoffs.set(this, context); return { setupBiometry: context }; },
  beforeUnmount() { handoffs.get(this)?.clear(); handoffs.delete(this); clearRecovery(); },
  steps: {
    index: AuthStepIndex,
    passphraseGeneration: AuthStepPassphraseGeneration,
    passphrase: AuthStepPassphrase,
    passphraseConfirmation: AuthStepPassphraseConfirmation,
    login: AuthStepLogin,
    pin: AuthStepPin,
    biometry: AuthStepBiometry,
    selectCryptos: MwSelectCryptosStep,
  },
};
</script>

<template>
  <MwSteps :steps="$options.steps" />
</template>
