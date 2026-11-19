<script>
import { portfolioReferenceValue } from '../lib/account/portfolio.js';
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
      const total = portfolioReferenceValue(this.$cryptos);
      if (total === null) return '—';
      return `≈ ${new Intl.NumberFormat(this.$i18n.locale, { style: 'currency', currency: 'USD', numberingSystem: 'latn' }).format(total)}`;
    },
    readingState() {
      if (portfolioReferenceValue(this.$cryptos) === null) return this.$t('Balance unavailable');
      return this.$cryptos.some(item => item.readState !== 'fresh') ? this.$t('Last known balance') : this.$t('Approximate USD value');
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
  <aside
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
      <button
        type="button"
        dir="ltr"
        class="&__portfolio-amount"
        :class="`&__portfolio-amount--${portfolioBalanceSize}`"
        :aria-label="$t($isHiddenBalance ? 'Show balance' : 'Hide balance')"
        :aria-pressed="$isHiddenBalance"
        @click="$account.toggleHiddenBalance()"
      >
        {{ $isHiddenBalance ? '*****' : portfolioTotal }}
      </button>
      <div class="&__portfolio-label">{{ $t('Portfolio value') }}</div>
      <div class="&__portfolio-change">{{ $isHiddenBalance ? '*****' : readingState }}</div>
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
  </aside>
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
      width: min(22.5rem, 35vw);
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
      width: 100%;
      overflow-wrap: anywhere;
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
