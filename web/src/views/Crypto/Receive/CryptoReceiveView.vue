<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoReceiveStepAccount from './CryptoReceiveStepAccount.vue';
import CryptoReceiveStepIndex from './CryptoReceiveStepIndex.vue';
import CryptoReceiveStepMoneroAccept from './CryptoReceiveStepMoneroAccept.vue';
import CryptoReceiveStepMoneroStatus from './CryptoReceiveStepMoneroStatus.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: CryptoReceiveStepIndex,
    account: CryptoReceiveStepAccount,
    moneroAccept: CryptoReceiveStepMoneroAccept,
    pin: MwPinStep,
    moneroStatus: CryptoReceiveStepMoneroStatus,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
