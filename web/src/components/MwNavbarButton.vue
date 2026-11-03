<script>
import MwButton from './MwButton.vue';

export default {
  components: {
    MwButton,
  },
};
</script>

<template>
  <MwButton
    class="&"
    type="base"
  >
    <slot />
  </MwButton>
</template>

<style lang="scss">
  .#{ $filename } {
    width: var(--spacing-5xl);
    height: 100%;
    flex-shrink: 0;
    padding: var(--spacing-lg);
  }
</style>
