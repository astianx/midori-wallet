<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import SettingsPinStepIndex from './SettingsPinStepIndex.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: SettingsPinStepIndex,
    pin: MwPinStep,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
