<script>
import MwButton from './MwButton.vue';
import MwButtonGroup from './MwButtonGroup.vue';
import MwModal from './MwModal.vue';

import eventBus from '../lib/eventBus.js';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    MwModal,
  },
  data() {
    return {
      show: false,
    };
  },
  mounted() {
    eventBus.on('MwErrorHardwareNotSupported', () => this.show = true);
  },
};
</script>

<template>
  <MwModal
    :show="show"
    :title="$t('Error')"
    @close="show = false"
  >
    {{ $t('Hardware keys are not supported by your device.') }}
    <template #footer>
      <MwButtonGroup>
        <MwButton
          type="primary-link"
          @click="$safeOpen('https://github.com/astianx/midori-wallet/issues')"
        >
          {{ $t('Read more') }}
        </MwButton>
      </MwButtonGroup>
    </template>
  </MwModal>
</template>
