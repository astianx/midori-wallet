<script>
import MwSteps from '../../components/MwSteps.vue';

import AuthStepBiometry from './AuthStepBiometry.vue';
import AuthStepIndex from './AuthStepIndex.vue';
import AuthStepLogin from './AuthStepLogin.vue';
import AuthStepPassphrase from './AuthStepPassphrase.vue';
import AuthStepPassphraseConfirmation from './AuthStepPassphraseConfirmation.vue';
import AuthStepPassphraseGeneration from './AuthStepPassphraseGeneration.vue';
import AuthStepPin from './AuthStepPin.vue';
import AuthStepBackup from './AuthStepBackup.vue';
import { clearRecovery } from '../../lib/account/recovery.js';
import MwSelectCryptosStep from '../../components/MwSelectCryptosStep.vue';

export default {
  components: {
    MwSteps,
  },
  beforeUnmount() { clearRecovery(); },
  steps: {
    backup: AuthStepBackup,
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
