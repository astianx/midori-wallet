<script>
import { CHART_PERIODS } from '../../../../config/market.js';

export default {
  props: {
    period: {
      type: String,
      required: true,
    },
  },
  emits: ['change'],
  data() {
    return {
      periods: Object.keys(CHART_PERIODS),
    };
  },
};
</script>

<template>
  <div class="&" role="group" :aria-label="$t('Chart period')">
    <div
      v-for="item of periods"
      :key="item"
      class="&__period"
    >
      <button
        type="button"
        class="&__button"
        :aria-pressed="period === item"
        @click="$emit('change', item)"
      >
        {{ item }}
      </button>
    </div>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    position: relative;
    display: flex;
    gap: var(--spacing-2xs);

    @include breakpoint(lg) {
      max-width: 30rem;
    }

    &__period {
      position: relative;
      flex-grow: 1;
      min-width: 0;
    }

    &__button {
      @include text-sm;
      display: flex;
      width: 100%;
      min-height: 2.75rem;
      align-items: center;
      justify-content: center;
      border-radius: 0.5rem;
      cursor: pointer;
      &[aria-pressed='true'] { background-color: var(--color-secondary-light); }
    }
  }
</style>
