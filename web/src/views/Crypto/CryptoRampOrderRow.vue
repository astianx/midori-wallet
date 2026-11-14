<script>
import { Amount } from '../../lib/account/domain.js';

export default {
  props: {
    order: {
      type: Object,
      required: true,
    },
  },
  computed: {
    description() {
      return this.order.kind === 'buy' ? this.$t('Buy {symbol}', { symbol: this.order.cryptoCurrency }) : this.$t('Sell {symbol}', { symbol: this.order.cryptoCurrency });
    },
    amount() {
      const value = this.order.kind === 'buy' ? this.order.fiatAmount : this.order.cryptoAmount;
      const currency = this.order.kind === 'buy' ? this.order.fiatCurrency : this.order.cryptoCurrency;
      return value == null ? '' : `${Amount.fromString(value, 18).toString()} ${currency}`;
    },
    status() {
      return {
        created: this.$t('Started'),
        awaiting_payment: this.$t('Awaiting payment'),
        processing: this.$t('Processing'),
        completed: this.$t('Completed'),
        failed: this.$t('Failed'),
        refunded: this.$t('Refunded'),
        expired: this.$t('Expired'),
      }[this.order.status] || this.order.status;
    },
    date() {
      const timestamp = new Date(this.order.updatedAt);
      const isCurrentYear = timestamp.getFullYear() === (new Date()).getFullYear();
      return this.$d(timestamp, isCurrentYear ? 'shortCurrentYear' : 'short');
    },
  },
};
</script>

<template>
  <div class="&">
    <div class="&__title">
      <div class="&__description">
        {{ description }}
      </div>
      <div
        class="&__amount"
        dir="ltr"
      >
        {{ amount }}
      </div>
    </div>
    <div class="&__subtitle">
      <div
        class="&__status"
        :class="{
          '&__status--positive': order.status === 'completed',
          '&__status--negative': ['failed','refunded','expired'].includes(order.status),
        }"
      >
        {{ status }}
      </div>
      <div class="&__date">
        {{ date }}
      </div>
    </div>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    padding: var(--spacing-md) var(--spacing-sm);
    border-radius: 0.625rem;

    &__title {
      @include text-md;
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: var(--spacing-sm);
      row-gap: var(--spacing-2xs);
    }

    &__subtitle {
      @include text-sm;
      display: flex;
      justify-content: space-between;
      color: var(--color-secondary);
      gap: var(--spacing-sm);
    }

    &__description {
      flex-shrink: 0;
    }

    &__amount {
      min-width: 0;
      max-width: 100%;
      margin-inline-start: auto;
      flex-shrink: 1;
      overflow-wrap: anywhere;
      text-align: end;
      font-variant-numeric: tabular-nums;
    }

    &__status {
      flex-shrink: 0;

      &--positive {
        color: var(--color-primary);
      }

      &--negative {
        color: var(--color-danger);
      }
    }

    &__date {
      flex-shrink: 1;
      @include ellipsis;
    }
  }
</style>
