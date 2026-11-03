<script>
import { measureText } from '../lib/helpers.js';

import MwAvatar from './MwAvatar.vue';
import MwButton from '../components/MwButton.vue';
import MwCryptoList from '../components/MwCryptoList.vue';

import BankCardIcon from '../assets/svg/bankCard.svg';
import PlusIcon from '../assets/svg/plus.svg';

const CHANGE_PERIODS = ['1D', '7D', '14D', '1M', '1Y'];

export default {
  components: {
    BankCardIcon,
    MwAvatar,
    MwButton,
    MwCryptoList,
    PlusIcon,
  },
  props: {
    active: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      changePeriodIndex: 0,
    };
  },
  computed: {
    portfolioTotal() {
      if (!this.$cryptos.length || this.$cryptos.some(item => !item.balance)) return '—';
      if (this.$cryptos.every(item => item.balance.value === 0n)) return this.$fiat(0);
      if (this.$cryptos.every(item => item.market?.price)) return this.$fiat(this.portfolioData.balance);
      const total = this.$cryptos.reduce((sum, item) => sum + item.balance.value, 0n);
      const units = `${total / 1000000n}.${(total % 1000000n).toString().padStart(6, '0')}`;
      return `≈ ${new Intl.NumberFormat(this.$i18n.locale, { style: 'currency', currency: 'USD' }).format(units)}`;
    },
    readingState() {
      return this.$cryptos.some(item => item.readState !== 'fresh') ? this.$t('Last known balance') : this.$t('Live prices unavailable');
    },
    hasMarket() { return this.$cryptos.length && this.$cryptos.every(item => item.market?.price); },
    portfolioData() {
      let totalBalance = 0;
      const changeAccumulator = CHANGE_PERIODS.reduce((result, key) => { result[key] = 0; return result; }, {});
      for (const item of this.$cryptos) {
        if (!item.market?.price || !item.balanceFiat) continue;
        const balance = item.balanceFiat;
        totalBalance += balance;
        const change = item.market.change || {};
        for (const period of CHANGE_PERIODS) {
          const percent = (change[period] || 0) / 100;
          changeAccumulator[period] += balance * percent;
        }
      }
      if (totalBalance > 0) {
        for (const period of CHANGE_PERIODS) {
          changeAccumulator[period] /= totalBalance;
        }
      }
      return {
        balance: totalBalance,
        changePercent: changeAccumulator,
      };
    },
    portfolioBalanceSize() {
      if (this.$isHiddenBalance) return 'normal';
      const str = this.portfolioTotal;
      const { width } = measureText(str);
      if (width < 120) return 'normal';
      if (width < 200) return 'large';
      return '';
    },
    changePeriod() {
      return CHANGE_PERIODS[this.changePeriodIndex];
    },
    changePeriodLabel() {
      switch (this.changePeriod) {
        case '1D':
          return this.$t('1 day');
        case '7D':
          return this.$t('7 days');
        case '14D':
          return this.$t('14 days');
        case '1M':
          return this.$t('1 month');
        case '1Y':
          return this.$t('1 year');
      }
    },
  },
  methods: {
    nextChangePeriod() {
      if (this.$isHiddenBalance) return this.$account.toggleHiddenBalance();
      this.changePeriodIndex = (this.changePeriodIndex + 1) % CHANGE_PERIODS.length;
    },
  },
};
</script>

<template>
  <div
    class="&"
    :class="{ '&--active': active }"
  >
    <div class="&__navbar">
      <div class="&__row">
        <MwButton
          @click="$router.push({ name: 'settings' })"
        >
          <MwAvatar
            class="&__avatar"
            :avatar="$user.avatar"
            own
            :size="48"
            :alt="$t('Settings')"
          />
        </MwButton>
        <MwButton
          v-if="$account.cards.isEnabled"
          class="&__cards"
          type="circle"
          @click="$router.push({ name: 'cards', force: true })"
        >
          <template #circle>
            <BankCardIcon />
          </template>
        </MwButton>
      </div>
      <div
        dir="ltr"
        class="&__portfolio-amount"
        :class="`&__portfolio-amount--${portfolioBalanceSize}`"
        @click="$account.toggleHiddenBalance()"
      >
        {{ $isHiddenBalance ? '*****' : portfolioTotal }}
      </div>
      <div class="&__portfolio-label"
        :title="hasMarket ? undefined : $t('Reference: USD 1 per token. Live prices unavailable.')">
        {{ $t('Portfolio value') }}
      </div>
      <div
        class="&__portfolio-change"
        :class="{
          '&__portfolio-change--positive': !$isHiddenBalance && portfolioData.changePercent[changePeriod] > 0,
          '&__portfolio-change--negative': !$isHiddenBalance && portfolioData.changePercent[changePeriod] < 0,
        }"
        @click="nextChangePeriod"
      >
        <template v-if="!$isHiddenBalance">
          <template v-if="hasMarket">
            {{ $n(portfolioData.changePercent[changePeriod], 'percent') }} ({{ changePeriodLabel }})
          </template>
          <template v-else>{{ readingState }}</template>
        </template>
        <template v-else>
          *****
        </template>
      </div>
    </div>

    <div class="&__content">
      <MwCryptoList
        class="&__content-list"
        :header="$t('Wallet')"
        :items="$cryptos"
        :selected="$route.params.cryptoId"
        :changePeriod="changePeriod"
        @select="(id) => $router.push({ name: 'crypto', params: { cryptoId: id }})"
      />
      <div class="&__add-crypto">
        <MwButton
          type="primary-link"
          @click="$router.push({ name: 'crypto.add', force: true })"
        >
          <PlusIcon />
          {{ $t('Add crypto') }}
        </MwButton>
      </div>
    </div>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    display: none;
    width: 100%;
    height: 100%;
    flex-direction: column;
    background-color: var(--color-background);
    overflow-y: auto;

    @include breakpoint(lg) {
      display: flex;
      width: 22.5rem;
      flex-shrink: 0;
      border-radius: 0.625rem;
    }

    &--active {
      display: flex;
    }

    &__navbar {
      padding:
        var(--spacing-3xl)
        var(--spacing-xl)
        var(--spacing-xl)
        var(--spacing-xl);
    }

    &__avatar {
      width: var(--spacing-4xl);
      height: var(--spacing-4xl);
    }

    &__row {
      display: flex;
      justify-content: space-between;
      margin-bottom: var(--spacing-3xl);
    }

    &__cards {
      flex: 0 0 var(--spacing-4xl);

      .mw-button__circle {
        width: var(--spacing-4xl);
        height: var(--spacing-4xl);
      }

      svg {
        width: var(--spacing-lg);
        height: var(--spacing-lg);
      }
    }

    &__portfolio-amount {
      @include ellipsis;
      margin-bottom: var(--spacing-2xs);
      cursor: pointer;
      text-align: center;
      &--large { @include text-lg; }
      &--normal { @include text-3xl; }
    }

    &__portfolio-label {
      @include text-sm;
      margin-bottom: var(--spacing-2xs);
      color: var(--color-secondary);
      text-align: center;
    }

    &__portfolio-change {
      @include text-sm;
      cursor: pointer;
      overflow-wrap: break-word;
      text-align: center;

      &--positive {
        color: var(--color-primary);
      }

      &--negative {
        color: var(--color-danger);
      }
    }

    &__content {
      display: flex;
      flex: 1 1 100%;
      flex-direction: column;
      padding:
        var(--spacing-xl)
        var(--spacing-xl)
        max(var(--spacing-3xl), env(safe-area-inset-bottom))
        var(--spacing-xl);
      gap: var(--spacing-2xs);
      @include breakpoint(lg) {
        overflow-y: auto;
        scrollbar-width: thin;
      }
    }

    &__content-list {
      flex-grow: 1;
    }

    &__add-crypto {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      justify-content: flex-end;
    }
  }
</style>
