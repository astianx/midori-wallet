<script>

import AxisY from './MwPriceChartAxisY.vue';
import MwLoader from '../MwLoader.vue';
import MwButton from '../MwButton.vue';
import Periods from './MwPriceChartPeriods.vue';

export default {
  components: {
    AxisY,
    MwLoader,
    MwButton,
    Periods,
    Apex: {},
  },
  props: {
    chartSeries: {
      type: Array,
      required: true,
    },
    period: {
      type: String,
      required: true,
    },
    isLoading: {
      type: Boolean,
      default: true,
    },
    error: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['changePeriod', 'crosshair', 'retry'],
  data() {
    return { keyboardIndex: -1 };
  },
  watch: {
    chartSeries() { this.keyboardIndex = -1; },
  },
  computed: {
    priceRange() {
      if (!this.chartSeries.length) return { min: 0, max: 0 };
      let max = this.chartSeries[0][1];
      let min = max;
      for (const item of this.chartSeries) {
        max = item[1] > max ? item[1] : max;
        min = item[1] < min ? item[1] : min;
      }
      return { min, max };
    },
    selectedIndex() {
      return this.keyboardIndex < 0 ? this.chartSeries.length - 1 : this.keyboardIndex;
    },
    selectedValue() {
      const point = this.chartSeries[this.selectedIndex];
      return point ? `${this.$d(point[0], 'short')} · ${this.$fiatPrecise(point[1], 'currency', { maximumFractionDigits: 6 })}` : '';
    },
    keyboardPosition() {
      const first = this.chartSeries[0][0];
      const last = this.chartSeries.at(-1)[0];
      return `${100 * (this.chartSeries[this.selectedIndex][0] - first) / (last - first || 1)}%`;
    },
  },
  methods: {
    explore(event) {
      const moves = { ArrowLeft: -1, ArrowDown: -1, ArrowRight: 1, ArrowUp: 1 };
      if (!Object.hasOwn(moves, event.key) && !['Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      this.keyboardIndex = event.key === 'Home' ? 0 : event.key === 'End' ? this.chartSeries.length - 1 : Math.max(0, Math.min(this.chartSeries.length - 1, this.selectedIndex + moves[event.key]));
      this.$emit('crosshair', this.chartSeries[this.keyboardIndex]);
    },
    stopExploring() {
      this.keyboardIndex = -1;
      this.$emit('crosshair');
    },
  },
  async loadApex() {
    this.components.Apex = (await import('./MwPriceChartApex.vue')).default;
  },
};
</script>

<template>
  <div class="&" :aria-busy="isLoading">
    <MwLoader
      v-if="isLoading"
      class="&__loader"
    />
    <div
      v-else-if="error || !chartSeries.length"
      class="&__error"
    >
      <p role="status">{{ $t('Price history unavailable') }}</p>
      <MwButton type="secondary" small @click="$emit('retry')">{{ $t('Try again') }}</MwButton>
    </div>
    <div
      v-else
      class="&__wrapper"
      tabindex="0"
      role="slider"
      :aria-label="$t('Explore price history')"
      :aria-valuemin="0"
      :aria-valuemax="chartSeries.length - 1"
      :aria-valuenow="selectedIndex"
      :aria-valuetext="selectedValue"
      @keydown="explore"
      @blur="stopExploring"
    >
      <Apex
        aria-hidden="true"
        :chartSeries="chartSeries"
        @crosshair="(chartPoint) => $emit('crosshair', chartPoint)"
      />
      <AxisY :priceRange="priceRange" />
      <span v-if="keyboardIndex >= 0" class="&__keyboard-point" :style="{ left: keyboardPosition }" aria-hidden="true" />
    </div>
    <Periods
      :period="period"
      @change="(period) => $emit('changePeriod', period)"
    />
  </div>
</template>

<style lang="scss">
  .#{ $filename } {

    user-select: none;

    &__wrapper {
      position: relative;
      isolation: isolate;
      padding: var(--spacing-md) 0;
      margin-bottom: var(--spacing-md);
      @include breakpoint(lg) {
        padding: var(--spacing-lg) 0;
      }
    }

    &__loader,
    &__error {
      height: calc(10rem + 3 * var(--spacing-md));
      @include breakpoint(lg) {
        height: calc(10rem + 2 * var(--spacing-lg) + var(--spacing-md));
      }
    }

    &__error {
      @include text-sm;
      display: flex;
      flex-direction: column;
      gap: var(--spacing-sm);
      align-items: center;
      justify-content: center;
    }

    &__keyboard-point {
      position: absolute;
      top: var(--spacing-md);
      bottom: var(--spacing-md);
      border-left: 1px dashed var(--color-primary);
      pointer-events: none;
    }
  }
</style>
