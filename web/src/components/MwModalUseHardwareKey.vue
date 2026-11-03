<script>
import MwButton from '../components/MwButton.vue';
import MwButtonGroup from '../components/MwButtonGroup.vue';
import MwModal from '../components/MwModal.vue';

import eventBus from '../lib/eventBus.js';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    MwModal,
  },
  data() {
    return {
      options: { show: false },
      type: ['mac', 'linux'].includes(this.env.VITE_PLATFORM) ? 'modal' : 'overlay',
    };
  },
  mounted() {
    eventBus.on('MwModalUseHardwareKey', (options) => {
      this.options = options;
    });
  },
  methods: {
    close() {
      this.options.cancel();
    },
  },
};
</script>

<template>
  <MwModal
    v-if="type === 'modal'"
    :show="options.show"
    :title="$t('Hardware key')"
    @close="close"
  >
    {{ $t('Use your hardware key.') }}
    <template #footer>
      <MwButtonGroup>
        <MwButton
          type="primary-link"
          @click="close"
        >
          {{ $t('Cancel') }}
        </MwButton>
      </MwButtonGroup>
    </template>
  </MwModal>
  <Teleport
    v-if="type === 'overlay'"
    to="body"
  >
    <Transition name="modal">
      <div
        v-if="options.show"
        class="&__overlay"
      />
    </Transition>
  </Teleport>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__overlay {
      @include overlay;
    }
  }
</style>
