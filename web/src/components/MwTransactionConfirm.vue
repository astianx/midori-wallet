<script>
import { cryptoToFiat } from '../lib/helpers.js';

import MwButton from '../components/MwButton.vue';
import MwButtonGroup from './MwButtonGroup.vue';
import MwCard from '../components/MwCard.vue';
import MwFormGroup from '../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../components/MwForm/MwFormTextareaReadonly.vue';
import MwPoweredBy from '../components/MwPoweredBy.vue';
import MwTokenInfo from '../components/MwTokenInfo.vue';

import ArrowDownIcon from '../assets/svg/arrowDown.svg';
import WalletSmallIcon from '../assets/svg/walletSmall.svg';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    MwCard,
    MwFormGroup,
    MwFormTextareaReadonly,
    MwPoweredBy,
    MwTokenInfo,
    ArrowDownIcon,
    WalletSmallIcon,
  },
  props: {
    transaction: {
      type: Object,
      default() {
        return {};
      },
    },
    powered: {
      type: Object,
      default: undefined,
    },
    isLoading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['confirm'],
  data() {
    return {
      fiatMode: false,
    };
  },
  computed: {
    crypto() {
      return this.transaction.crypto || this.$wallet.crypto;
    },
    platform() {
      return this.transaction.platform || this.$wallet.platform;
    },
    amount() {
      if (this.fiatMode && this.transaction.price !== undefined) {
        return this.$fiatPrecise(cryptoToFiat(this.transaction.amount, this.transaction.price));
      } else {
        return `${this.transaction.amount} ${this.crypto.symbol}`;
      }
    },
    fee() {
      if (!this.transaction.fee) return undefined;
      if (this.fiatMode && this.transaction.pricePlatform !== undefined) {
        return this.$t('{sign}{fee} fee', {
          fee: this.$fiatPrecise(cryptoToFiat(this.transaction.fee, this.transaction.pricePlatform)),
          sign: '+',
        });
      } else {
        return this.$t('{sign}{fee} {symbol} fee', {
          sign: '+',
          fee: this.transaction.fee,
          symbol: this.platform.symbol,
        });
      }
    },
    amountTo() {
      if (this.fiatMode && this.transaction.priceTo !== undefined) {
        return `≈\xa0${this.$fiatPrecise(cryptoToFiat(this.transaction.amountTo, this.transaction.priceTo))}`;
      } else {
        return `≈\xa0${this.transaction.amountTo} ${this.transaction.to.crypto.symbol}`;
      }
    },
    cardTitle() {
      if (this.transaction.cardAction === 'topup') {
        if (this.fiatMode && this.transaction.price !== undefined) {
          return `${this.$fiatPrecise(cryptoToFiat(this.transaction.topup, this.transaction.price))}`;
        } else {
          return `${this.transaction.topup} ${this.transaction.card.symbol}`;
        }
      } else {
        return this.transaction.product.name;
      }
    },
  },
};
</script>

<template>
  <MwTokenInfo
    class="&__info"
    :crypto="crypto"
    :platform="platform"
    :title="amount"
    :subtitles="fee && [fee]"
    @click="fiatMode = !fiatMode"
  />
  <ArrowDownIcon class="&__arrow" />

  <MwTokenInfo
    v-if="transaction.exchange === true"
    class="&__info"
    :crypto="transaction.to.crypto"
    :platform="transaction.to.platform"
    :title="amountTo"
    @click="fiatMode = !fiatMode"
  />

  <div
    v-if="['topup', 'buy'].includes(transaction.cardAction)"
    class="&__card"
    @click="fiatMode = !fiatMode"
  >
    <MwCard
      :productId="transaction.cardAction === 'topup' ? transaction.card.product.id : transaction.product.id"
      size="medium"
      :lastFourDigits="transaction.card?.lastFourDigits"
    />
    <div class="&__card-title">
      {{ cardTitle }}
    </div>
  </div>

  <MwFormGroup class="&__content">
    <template v-if="transaction.exchange !== true && transaction.address === 'your wallet'">
      <MwFormTextareaReadonly
        :value="$t('Your wallet')"
        :label="$t('Wallet address')"
        inheritDir
      >
        <template #before>
          <WalletSmallIcon />
        </template>
      </MwFormTextareaReadonly>
    </template>
    <template v-if="transaction.address !== 'your wallet' && !transaction.cardAction">
      <MwFormTextareaReadonly
        v-if="transaction.alias"
        :value="transaction.alias"
        :label="$t('Alias')"
      />
      <MwFormTextareaReadonly
        :value="transaction.address"
        :label="$t('Wallet address')"
      />
      <MwFormTextareaReadonly
        v-if="transaction.meta?.destinationTag"
        :value="transaction.meta?.destinationTag"
        :label="$t('Destination tag / memo')"
      />
      <MwFormTextareaReadonly
        v-if="transaction.meta?.invoiceID"
        :value="transaction.meta?.invoiceID"
        :label="$t('Invoice ID')"
      />
      <MwFormTextareaReadonly
        v-if="transaction.meta?.memo"
        :value="transaction.meta?.memo"
        :label="$t('Memo')"
      />
      <MwFormTextareaReadonly
        v-if="transaction.extraId"
        :value="transaction.extraId"
        :label="$t('Extra ID')"
      />
    </template>
  </MwFormGroup>

  <MwButtonGroup>
    <MwButton
      type="primary"
      :isLoading="isLoading"
      @click="$emit('confirm')"
    >
      {{ $t('Confirm') }}
    </MwButton>
    <MwPoweredBy
      v-if="powered"
      :powered="powered"
    />
  </MwButtonGroup>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__info {
      cursor: pointer;
    }

    &__content {
      flex-grow: 1;
    }

    &__arrow {
      width: var(--spacing-xl);
      height: var(--spacing-xl);
      align-self: center;
    }

    &__card {
      display: flex;
      flex-direction: column;
      align-items: center;
      cursor: pointer;
      gap: var(--spacing-md);
    }

    &__card-title {
      @include text-md;
      @include text-bold;
    }
  }
</style>
