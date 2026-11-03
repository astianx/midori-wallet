<script>
import MwPinStep from '../../components/MwPinStep.vue';
import MwSteps from '../../components/MwSteps.vue';

import MwSelectCryptosStep from '../../components/MwSelectCryptosStep.vue';
import UnlockStepIndex from './UnlockStepIndex.vue';
import UnlockStepSynchronization from './UnlockStepSynchronization.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: UnlockStepIndex,
    synchronization: UnlockStepSynchronization,
    pin: MwPinStep,
    selectCryptos: MwSelectCryptosStep,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
