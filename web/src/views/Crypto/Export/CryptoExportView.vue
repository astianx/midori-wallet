<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoExportStepIndex from './CryptoExportStepIndex.vue';
import CryptoExportStepShow from './CryptoExportStepShow.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: CryptoExportStepIndex,
    pin: MwPinStep,
    show: CryptoExportStepShow,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
