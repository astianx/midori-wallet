<script>
import { cryptoSubtitleWithSymbol } from '../../../lib/helpers.js';

import MwNavbar from '../../../components/MwNavbar.vue';
import MwNavbarButton from '../../../components/MwNavbarButton.vue';
import MwPriceChart from '../../../components/MwPriceChart/MwPriceChart.vue';
import MwRefreshButton from '../../../components/MwRefreshButton.vue';
import SmartLayout from '../../../layouts/SmartLayout.vue';

import CryptoIndexActions from './CryptoIndexActions.vue';
import CryptoIndexBalance from './CryptoIndexBalance.vue';
import CryptoIndexBuySell from './CryptoIndexBuySell.vue';
import CryptoIndexConfirmRemove from './CryptoIndexConfirmRemove.vue';
import CryptoIndexDropdownMenu from './CryptoIndexDropdownMenu.vue';
import CryptoIndexPrice from './CryptoIndexPrice.vue';
import CryptoIndexTools from './CryptoIndexTools.vue';

import ArrowLeftIcon from '../../../assets/svg/arrowLeft.svg';

export default {
  components: {
    CryptoIndexActions,
    CryptoIndexBalance,
    CryptoIndexBuySell,
    CryptoIndexConfirmRemove,
    CryptoIndexDropdownMenu,
    CryptoIndexPrice,
    CryptoIndexTools,
    SmartLayout,
    MwPriceChart,
    MwNavbar,
    MwNavbarButton,
    MwRefreshButton,
    ArrowLeftIcon,
  },
  data() {
    return {
      period: '1D',
      chartPoint: undefined,
      chartSeries: [],
      isLoadingChart: true,
      chartError: false,
      chartRequest: 0,
      showConfirmRemove: false,
      subtitleWithSymbol: cryptoSubtitleWithSymbol(this.$wallet),
    };
  },
  computed: {
    isLoading() {
      return this.$walletState === this.$STATE_LOADING;
    },
    price() {
      return this.market?.price || 0;
    },
    chartPrice() {
      return this.chartSeries.at(-1)?.[1] || 0;
    },
    change() {
      const first = this.chartSeries[0]?.[1];
      return first ? (this.chartPrice - first) / first : 0;
    },
    market() {
      return this.$cryptos.find((item) => {
        return item.crypto._id === this.$wallet.crypto._id;
      })?.market;
    },
  },
  watch: {
    '$wallet.crypto._id'() { this.loadPriceChart(); },
    '$currency'() { this.loadPriceChart(); },
  },
  mounted() {
    if (this.$walletState === this.$STATE_LOADED) {
      this.$wallet.cleanup();
      this.$account.emit('update');
    }
    this.loadPriceChart();
  },
  beforeUnmount() {
    this.chartRequest++;
  },
  methods: {
    async refresh() {
      await Promise.all([
        this.$loadWallet(),
        this.loadPriceChart(),
      ]);
    },
    async loadPriceChart(period = this.period) {
      const request = ++this.chartRequest;
      const crypto = this.$wallet.crypto;
      const currency = this.$currency;
      this.period = period;
      this.isLoadingChart = true;
      this.chartError = false;
      this.chartPoint = undefined;
      this.chartSeries = [];
      try {
        if (!crypto.marketId) return;
        const [, data] = await Promise.all([
          MwPriceChart.loadApex(),
          this.$account.market.getChartData(crypto._id, period, currency),
        ]);
        if (request !== this.chartRequest) return;
        if (data.symbol !== crypto.symbol || data.currency !== currency || data.period !== period || !Array.isArray(data.prices) || data.prices.length < 2) throw new Error('Invalid price history');
        this.chartSeries = data.prices;
      } catch (err) {
        if (request === this.chartRequest) this.chartError = true;
      } finally {
        if (request === this.chartRequest) this.isLoadingChart = false;
      }
    },
    setChartPoint(chartPoint) {
      if (chartPoint) {
        const timestamp = new Date(chartPoint[0]);
        const isCurrentYear = timestamp.getFullYear() === (new Date()).getFullYear();
        const format = this.period === '1Y' ? 'chart1Y' : (isCurrentYear ? 'shortCurrentYear': 'short');
        this.chartPoint = {
          timestamp: this.$d(chartPoint[0], format),
          price: chartPoint[1],
        };
      } else {
        this.chartPoint = undefined;
      }
    },
    async remove() {
      this.showConfirmRemove = false;
      await this.$account.removeWallet(this.$wallet.crypto);
      this.$router.up();
    },
  },
};
</script>
<template>
  <SmartLayout
    wide
    :isLoading="isLoading"
    @refresh="refresh"
  >
    <template #navbar="{ back }">
      <MwNavbar
        :title="$wallet.crypto.name"
        :description="subtitleWithSymbol"
      >
        <template #left>
          <MwNavbarButton
            :title="$t('Back')"
            :aria-label="$t('Back')"
            @click="back"
          >
            <ArrowLeftIcon class="rtl-mirror" />
          </MwNavbarButton>
        </template>
        <template #right>
          <MwRefreshButton
            :isLoading="isLoading"
            @click="refresh"
          />
          <CryptoIndexDropdownMenu
            @remove="showConfirmRemove = true"
          />
        </template>
      </MwNavbar>
    </template>

    <section v-if="$wallet.crypto.marketId" class="&__header-container" :aria-label="$t('Price history')">
      <div class="&__header">
        <CryptoIndexPrice
          :price="chartPrice"
          :change="change"
          :chartPoint="chartPoint"
          :isLoading="isLoadingChart"
        />
        <CryptoIndexBuySell />
      </div>
      <MwPriceChart
        :chartSeries="chartSeries"
        :period="period"
        :isLoading="isLoadingChart"
        :error="chartError"
        @changePeriod="loadPriceChart"
        @crosshair="setChartPoint"
        @retry="loadPriceChart()"
      />
    </section>
    <CryptoIndexBalance :price="price" />
    <CryptoIndexActions />
    <div
      v-if="$walletState === $STATE_ERROR"
      class="&__error"
    >
      {{ $account.unknownError() }}
    </div>

    <CryptoIndexTools
      @remove="showConfirmRemove = true"
    />
    <CryptoIndexConfirmRemove
      :show="showConfirmRemove"
      @confirm="remove"
      @cancel="showConfirmRemove = false"
    />
  </SmartLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__header-container {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-lg);
    }

    &__header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--spacing-lg);
    }

    &__error {
      @include text-md;
      flex-grow: 1;
    }
  }
</style>
