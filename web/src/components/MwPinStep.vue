<script>
import AuthStepLayout from '../layouts/AuthStepLayout.vue';
import MainLayout from '../layouts/MainLayout.vue';

import MwPin from './MwPin.vue';
import MwStep from './MwStep.vue';

export default {
  components: {
    AuthStepLayout,
    MwPin,
    MainLayout,
  },
  extends: MwStep,
};
</script>

<template>
  <component
    :is="args.layout"
    :title="$t('Enter your PIN')"
  >
    <MwPin
      :mode="args.mode"
      @success="args.success"
    />
  </component>
</template>
