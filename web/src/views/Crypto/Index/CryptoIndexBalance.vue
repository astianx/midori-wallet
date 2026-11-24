<script>
import { groupAssets, assetFamily } from '../../../lib/account/stablecoin-accounts.js';
import MwStablecoinBalances from '../../../components/MwStablecoinBalances.vue';
import { cryptoToFiat } from '../../../lib/helpers.js';

export default {
  components: { MwStablecoinBalances },
  props: {
    price: {
      type: Number,
      default: 0,
    },
  },
  computed: {
    family() { return assetFamily(this.$wallet.crypto); },
    account() { return groupAssets(this.$cryptos, this.$wallet.crypto._id).find(item => item.family === this.family); },
    amount() {
      if (this.family) return this.account?.balance ? `${this.account.balance} ${this.family}` : this.$t('Balance unavailable');
      if (this.$walletState === this.$STATE_LOADING) return '...';
      if (this.$walletState === this.$STATE_ERROR) return '⚠️';
      return this.$wallet.balance ? `${this.$wallet.balance} ${this.$wallet.crypto.symbol}` : this.$t('Balance unavailable');
    },
    fiat() {
      if (this.$walletState === this.$STATE_LOADING) return '...';
      if (this.$walletState === this.$STATE_ERROR) return '⚠️';
      if (!this.$wallet.balance || !this.price) return this.$t('Live prices unavailable');
      const fiat = cryptoToFiat(this.$wallet.balance, this.price);
      return this.$fiat(fiat);
    },
  },
};
</script>

<template>
  <div
    v-if="$walletState === $STATE_LOADED || $walletState === $STATE_LOADING"
    class="&"
  >
    <div class="&__header">
      {{ $t('Balance') }}
    </div>

    <button
      type="button"
      :aria-label="$t($isHiddenBalance ? 'Show balance' : 'Hide balance')"
      :aria-pressed="$isHiddenBalance"
      class="&__balance"
      @click="$account.toggleHiddenBalance()"
    >
      <div
        class="&__amount"
        :title="$isHiddenBalance ? undefined : amount"
        dir="ltr"
      >
        {{ $isHiddenBalance ? '••••' : amount }}

      </div>
      <div
        v-if="$wallet.crypto.coingecko"
        class="&__fiat"
        :title="$isHiddenBalance ? undefined : fiat"
        dir="ltr"
      >
        {{ $isHiddenBalance ? '••••' : fiat }}
      </div>
    </button>
    <MwStablecoinBalances v-if="family" />
        <a
          v-if="$wallet.crypto._id === 'monero@monero' && $wallet.balance.value === 0n && !$isHiddenBalance"
          @click.stop="$safeOpen('https://github.com/astianx/midori-wallet/issues')"
        >
          {{ $t('Support') }}
        </a>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-2xs);

    &__header {
      @include text-sm;
      color: var(--color-secondary);
    }

    &__balance {
      display: flex;
      width: 100%;
      text-align: start;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      column-gap: var(--spacing-md);
      cursor: pointer;
    }

    &__amount {
      @include text-md;
      @include text-bold;
      overflow-wrap: anywhere;
      white-space: normal;
      min-width: 0;

      display: flex;
      gap: var(--spacing-xs);
    }

    &__fiat {
      @include text-sm;
      @include text-bold;
      overflow-wrap: anywhere;
      white-space: normal;
      min-width: 0;
    }
  }
</style>
