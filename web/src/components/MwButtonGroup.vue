<script>
export default {
  props: {
    type: {
      type: String,
      default: '',
    },
  },
};
</script>

<template>
  <div
    class="&"
    :class="{
      [`&--${type}`]: type,
    }"
  >
    <slot />
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-md);

    &--horizontal {
      flex-direction: row;
      flex-wrap: wrap;
      > * { min-width: 0; max-width: 100%; }
    }

    &--circle {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(6rem, 100%), 1fr));
      justify-items: center;
      gap: var(--spacing-2xs);
    }
  }
</style>
