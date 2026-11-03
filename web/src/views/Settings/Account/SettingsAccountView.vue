<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import SettingsAccountStepIndex from './SettingsAccountStepIndex.vue';
import SettingsAccountStepRemove from './SettingsAccountStepRemove.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: SettingsAccountStepIndex,
    remove: SettingsAccountStepRemove,
    pin: MwPinStep,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
