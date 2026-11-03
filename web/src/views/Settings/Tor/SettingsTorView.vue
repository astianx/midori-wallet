<script>
import MwSteps from '../../../components/MwSteps.vue';

import SettingsTorStepIndex from './SettingsTorStepIndex.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: SettingsTorStepIndex,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
