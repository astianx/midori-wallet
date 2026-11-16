<script>
import MwNavbarButton from './MwNavbarButton.vue';

import RefreshIcon from '../assets/svg/refresh.svg';

export default {
  components: {
    MwNavbarButton,
    RefreshIcon,
  },
  props: {
    isLoading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['click'],
  methods: {
    click() {
      if (!this.isLoading) this.$emit('click');
    },
  },
};

</script>

<template>
  <MwNavbarButton
    class="&"
    :title="$t('Refresh')"
    :aria-label="$t('Refresh')"
    :disabled="isLoading"
    :aria-busy="isLoading"
    @click="click"
  >
    <RefreshIcon
      :class="{ '&__spinner': isLoading }"
      aria-hidden="true"
    />
  </MwNavbarButton>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;
    display: flex;

    &__spinner {
      animation: rotation180 0.5s infinite linear;
    }
  }
</style>
