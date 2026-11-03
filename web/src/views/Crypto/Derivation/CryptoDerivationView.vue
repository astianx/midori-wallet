<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoDerivationStepIndex from './CryptoDerivationStepIndex.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: CryptoDerivationStepIndex,
    pin: MwPinStep,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
