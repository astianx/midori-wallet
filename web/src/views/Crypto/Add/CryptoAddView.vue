<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoAddStepCustomToken from './CryptoAddStepCustomToken.vue';
import CryptoAddStepFilterBlockchain from './CryptoAddStepFilterBlockchain.vue';
import CryptoAddStepIndex from './CryptoAddStepIndex.vue';
import CryptoAddStepQr from './CryptoAddStepQr.vue';
import CryptoAddStepSelectBlockchain from './CryptoAddStepSelectBlockchain.vue';
import CryptoAddStepTokenInfo from './CryptoAddStepTokenInfo.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: CryptoAddStepIndex,
    customToken: CryptoAddStepCustomToken,
    selectBlockchain: CryptoAddStepSelectBlockchain,
    filterBlockchain: CryptoAddStepFilterBlockchain,
    tokenInfo: CryptoAddStepTokenInfo,
    qr: CryptoAddStepQr,
    pin: MwPinStep,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
    :initialStorage="{ platform: 'ethereum@ethereum' }"
  />
</template>
