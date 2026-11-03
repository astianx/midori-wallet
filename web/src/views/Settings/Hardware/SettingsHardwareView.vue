<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import SettingsHardwareStepIndex from './SettingsHardwareStepIndex.vue';
import SettingsHardwareStepPassphrase from './SettingsHardwareStepPassphrase.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: SettingsHardwareStepIndex,
    pin: MwPinStep,
    passphrase: SettingsHardwareStepPassphrase,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
