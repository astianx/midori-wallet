<script>
import VueApexCharts from 'vue3-apexcharts';

export default {
  components: {
    apexchart: VueApexCharts,
  },
  props: {
    chartSeries: {
      type: Array,
      required: true,
    },
  },
  emits: ['crosshair'],
  data() {
    const firstPrice = this.chartSeries[0][1];
    const lastPrice = this.chartSeries[this.chartSeries.length - 1][1];
    const color = firstPrice <= lastPrice ? 'var(--color-primary-brand)' : 'var(--color-danger)';
    return {
      dataPointIndex: -1,
      chartOptions: {
        chart: {
          animations: { enabled: false },
          type: 'area',
          sparkline: { enabled: true },
        },
        tooltip: { enabled: true },
        colors: [color],
        fill: {
          type: 'gradient',
          gradient: {
            shadeIntensity: 0,
            opacityFrom: 0.2,
            opacityTo: 0,
            gradientFromColors: [color],
            gradientToColors: [color],
          },
        },
        stroke: {
          width: 1,
        },
        markers: {
          strokeColors: 'var(--color-background)',
          strokeWidth: 1,
          strokeOpacity: 1,
          hover: {
            size: 3,
          },
        },
        xaxis: {
          type: 'datetime',
          crosshairs: {
            opacity: 1,
            stroke: {
              color,
              width: 1,
              dashArray: 2,
            },
          },
        },
      },
      series: [
        {
          name: 'price',
          data: this.chartSeries,
        },
      ],
    };
  },
  methods: {
    mouseMove(event) {
      this.updateTooltip(true);
      const bounds = this.$refs.apex?.chart?.w.globals.dom.Paper.node.getBoundingClientRect();
      const clientX = event.clientX ?? event.touches?.[0]?.clientX;
      if (!bounds?.width || !Number.isFinite(clientX)) return;
      const points = this.series[0].data;
      const timestamp = points[0][0] + Math.max(0, Math.min(1, (clientX - bounds.left) / bounds.width)) * (points.at(-1)[0] - points[0][0]);
      let dataPointIndex = 0;
      for (let index = 1; index < points.length; index++) {
        if (Math.abs(points[index][0] - timestamp) < Math.abs(points[dataPointIndex][0] - timestamp)) dataPointIndex = index;
      }
      if (this.dataPointIndex !== dataPointIndex) {
        this.dataPointIndex = dataPointIndex;
        this.$emit('crosshair', this.series[0].data[dataPointIndex]);
        if (this.env.VITE_PLATFORM === 'ios') window.taptic.tap();
      }
    },
    mouseLeave() {
      this.$emit('crosshair');
      this.updateTooltip(false);
    },
    touchstart(e) {
      this.touchmove(e);
    },
    touchmove(e) {
      this.mouseMove(e);
    },
    touchend() {
      this.$emit('crosshair');
      this.updateTooltip(false);
    },
    touchcancel() {
      this.$emit('crosshair');
      this.updateTooltip(false);
    },
    updateTooltip(enabled) {
      if (enabled === this.chartOptions.tooltip.enabled) return;
      if (!enabled) this.dataPointIndex = -1;
      this.chartOptions = { ...this.chartOptions, ...{ tooltip: { enabled } } };
    },
  },
};
</script>

<template>
  <apexchart
    ref="apex"
    class="&"
    height="160px"
    data-prevent-scroll
    :options="chartOptions"
    :series="series"
    @mouseMove="mouseMove"
    @mouseLeave="mouseLeave"
    @touchend="touchend"
    @touchcancel="touchcancel"
    @touchstart="touchstart"
    @touchmove.prevent="touchmove"
  />
</template>

<style lang="scss">
  .#{ $filename } {
    * {
      transition: none !important;
      @media (pointer: coarse) {
        pointer-events: none;
      }
    }

    .apexcharts-tooltip {
      display: none;
    }
  }
</style>
