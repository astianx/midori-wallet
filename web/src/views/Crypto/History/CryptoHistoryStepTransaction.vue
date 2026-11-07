<script>
import * as MoneroSymbols from '../../../lib/ui-runtime/monero-symbols.js';
import { Transaction, errors } from '../../../lib/account/domain.js';

import BaseExchange from '../../../lib/exchanges/BaseExchange.js';
import { cryptoToFiat } from '../../../lib/helpers.js';
import { onShowOnHide } from '../../../lib/mixins.js';

import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwPoweredBy from '../../../components/MwPoweredBy.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import ArrowDownIcon from '../../../assets/svg/arrowDown.svg';
import CoinsIcon from '../../../assets/svg/coins.svg';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwFormGroup,
    MwFormTextareaReadonly,
    MwPoweredBy,
    ArrowDownIcon,
    CoinsIcon,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  onHide() { clearTimeout(this.$options.refreshTimer); this.$options.hidden = true; },
  async onShow() {
    this.$options.hidden = false;
    if (this.transaction.chainStatus) this.refreshTransaction();
    if (this.transaction.exchange) {
      try {
        this.transaction = await this.$account.exchanges.reexchangifyTransaction(this.transaction);
      } catch (err) {
        console.error('Midori Wallet operation failed');
      }
    }
  },
  data() {
    return {
      transaction: this.args.transaction,
      price: this.args.price,
      isLoading: false,
    };
  },
  computed: {
    from() {
      return this.transaction.from === MoneroSymbols.HIDDEN_ADDRESS
        ? this.$t('Stealth address')
        : this.transaction.from;
    },
    to() {
      if (this.exchange) return this.transaction.exchange.to;
      if (this.transaction.recipientTag) return `@${this.transaction.recipientTag}`;
      return this.transaction.to === MoneroSymbols.HIDDEN_ADDRESS
        ? this.$t('Stealth address')
        : this.transaction.to;
    },
    amount() {
      return `${this.transaction.incoming ? '+' : '-'}${this.transaction.amount} ${this.$wallet.crypto.symbol}`;
    },
    amountConverted() {
      return this.$fiat(cryptoToFiat(this.transaction.amount, this.price));
    },
    status() {
      if (this.transaction.chainStatus === 'reverted') return this.$t('Payment reverted');
      if (this.transaction.chainStatus === 'expired') return this.$t('Payment not sent');
      if (this.transaction.chainStatus === 'pending') return this.$t('Confirming payment');
      if (this.transaction.chainStatus === 'reorganized') return this.$t('Reorganized');
      if (this.transaction.chainStatus === 'included') return this.$t('Included, awaiting confirmations');
      if (this.transaction.chainStatus === 'confirmed') return this.$t('Confirmed');
      const { status, confirmations, minConfirmations, timestamp } = this.transaction;
      if (status === Transaction.STATUS_PENDING) {
        return this.$t('Pending confirmation ({confirmations})', {
          confirmations: `${confirmations}/${minConfirmations}`,
        });
      } else if (status === Transaction.STATUS_SUCCESS) {
        return this.$d(timestamp, 'short');
      } else if (status === Transaction.STATUS_FAILED) {
        return this.$t('Failed');
      }
    },
    exchange() {
      if (!this.transaction.exchange) return;
      const { status } = this.transaction.exchange;
      let text;
      if (status === BaseExchange.STATUS_PENDING) {
        text = this.$t('Awaiting transaction confirmation.');
      } else if (status === BaseExchange.STATUS_EXCHANGING) {
        text = this.$t('Deposit has been received. Awaiting swap.');
      } else if (status === BaseExchange.STATUS_REQUIRED_TO_ACCEPT) {
        text = this.$t('Please accept transaction to receive funds.');
      } else if (status === BaseExchange.STATUS_HOLD) {
        text = this.$t('Please contact {exchange} to pass KYC.', {
          exchange: this.transaction.exchange.providerInfo.name,
        });
      } else if (status === BaseExchange.STATUS_REFUNDED) {
        text = this.$t('Swap failed and funds were refunded to your wallet.');
      } else if (status === BaseExchange.STATUS_FAILED) {
        text = this.$t('Swap failed. Please contact {exchange}.', {
          exchange: this.transaction.exchange.providerInfo.name,
        });
      }
      return {
        amount: this.transaction.incoming ?
          `${this.transaction.exchange.amountFrom} ${this.transaction.exchange.cryptoFrom.symbol}` :
          `${this.transaction.exchange.amountTo} ${this.transaction.exchange.cryptoTo.symbol}`,
        status: text,
        isStatusDanger: [
          BaseExchange.STATUS_FAILED,
          BaseExchange.STATUS_REFUNDED,
          BaseExchange.STATUS_HOLD,
        ].includes(status),
        hasAcceptButton: status === BaseExchange.STATUS_REQUIRED_TO_ACCEPT,
      };
    },
  },
  methods: {
    async refreshTransaction() {
      try { const transaction = await this.$wallet.loadTransaction(this.transaction.id); if (!this.$options.hidden) this.transaction = transaction; } catch { /* Keep the last confirmed details while reconnecting. */ }
      if (!this.$options.hidden && ['pending','included','reorganized'].includes(this.transaction.chainStatus)) this.$options.refreshTimer = setTimeout(() => this.refreshTransaction(), 3000);
    },
    async accelerate() {
      this.isLoading = true;
      try {
        const replacement = await this.$wallet.estimateReplacement(this.transaction);
        const pricePlatform = await this.$account.market.getPrice(this.$wallet.platform._id, this.$currency);
        this.updateStorage({ replacement, pricePlatform, transaction: this.transaction });
        this.next('accelerate');
      } catch (err) {
        if (err instanceof errors.BigAmountError) {
          this.updateStorage({
            status: false,
            message: this.$t('Not enough funds for acceleration.'),
            action: this.$t('Back'),
          });
          this.next('accelerateStatus');
          return;
        }
        this.updateStorage({
          status: false,
        });
        this.next('accelerateStatus');
      } finally {
        this.isLoading = false;
      }
    },
    accept() {
      this.updateStorage({
        payoutHash: this.transaction.exchange.payoutHash,
      });
      this.next('moneroAccept');
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Transaction')" :allowReadFailure="!!transaction.chainStatus">
    <div class="&__header">
      <div
        class="&__amount-crypto"
        :class="{
          '&__amount-crypto--positive': transaction.incoming,
          '&__amount-crypto--negative': !transaction.incoming,
        }"
        dir="ltr"
      >
        {{ amount }}
      </div>
      <div
        v-if="price"
        class="&__amount-fiat"
        dir="ltr"
      >
        {{ amountConverted }}
      </div>
      <div class="&__status">
        {{ status }}
      </div>

      <template v-if="exchange">
        <ArrowDownIcon
          class="&__arrow"
          :class="{
            '&__arrow--up': transaction.incoming,
          }"
        />
        <div
          class="&__amount-crypto"
          dir="ltr"
        >
          {{ exchange.amount }}
        </div>
        <div
          v-if="exchange.status"
          class="&__status"
          :class="{ '&__status--danger': exchange.isStatusDanger }"
        >
          {{ exchange.status }}
        </div>
      </template>
    </div>

    <div
      v-if="exchange?.hasAcceptButton"
      class="&__accept"
    >
      <MwButton
        type="circle"
        @click="accept"
      >
        <template #circle>
          <CoinsIcon />
        </template>
        {{ $t('Accept') }}
      </MwButton>
    </div>

    <MwFormGroup class="&__info">
      <MwFormTextareaReadonly v-if="transaction.intentId && !transaction.incoming" :label="$t('Midori fee')" value="0" />
      <MwFormTextareaReadonly v-if="transaction.feeMode === 'token'" :label="$t('Network cost')" :value="transaction.networkFee !== undefined ? `${transaction.networkFee} ${transaction.symbol}` : $t('Up to {amount} {symbol}', { amount: transaction.maxNetworkFee, symbol: transaction.symbol })" />
      <MwFormTextareaReadonly v-if="transaction.feeMode === 'sponsored'" :label="$t('Network cost')" :value="$t('Sponsored by Midori')" />
      <p v-if="transaction.accountingState === 'review'" role="status">{{ $t('The payment was processed. Its network cost is being checked.') }}</p>
      <MwFormTextareaReadonly v-if="transaction.chainId" :label="$t('Network')" value="Base" />
      <MwFormTextareaReadonly v-if="transaction.origin" :label="$t('Payment origin')" :value="transaction.origin === 'midori' ? 'Midori Wallet' : $t('External or unverified')" />
      <template v-if="exchange && !transaction.incoming">
        <MwFormTextareaReadonly
          v-if="transaction.exchange.payoutHash"
          :label="$t('Output transaction ID')"
          :value="transaction.exchange.payoutHash"
        />
        <MwFormTextareaReadonly
          v-if="transaction.exchange.refundHash"
          :label="$t('Refund transaction ID')"
          :value="transaction.exchange.refundHash"
        />
      </template>
      <MwFormTextareaReadonly
        v-if="!transaction.incoming && to !== 'your wallet'"
        :label="$t('Recipient')"
        :value="to"
      />
      <details v-if="!transaction.incoming && transaction.recipientTag"><summary>{{ $t('View wallet address') }}</summary><MwFormTextareaReadonly :value="transaction.to" :label="$t('Wallet address')" /></details>
      <MwFormTextareaReadonly
        v-if="transaction.incoming && transaction.from && !exchange"
        :label="$t('From')"
        :value="from"
      />
      <MwFormTextareaReadonly
        v-if="!transaction.incoming && transaction.fee !== undefined"
        :label="$t('Fee')"
        :value="`${transaction.fee} ${$wallet.crypto.type === 'coin'
          ? $wallet.crypto.symbol : $wallet.platform.symbol}`
          + `${['ethereum@optimism', 'ethereum@base'].includes($wallet.platform._id) ? ' + L1' : ''}`"
      />
      <MwFormTextareaReadonly
        v-if="transaction.meta?.txKey"
        :label="$t('Transaction key')"
        :value="transaction.meta.txKey"
      />
      <MwFormTextareaReadonly
        v-if="transaction.transactionHash"
        :label="$t('Transaction ID')"
        :value="transaction.transactionHash"
      />
      <MwButton
        v-if="transaction.url"
        type="primary-link"
        @click="$safeOpen(transaction.url)"
      >
        {{ $t('View in Block Explorer') }}
      </MwButton>
    </MwFormGroup>
    <MwButtonGroup>
      <template v-if="exchange">
        <MwButton
          type="primary-light"
          @click="$safeOpen(transaction.exchange.trackUrl)"
        >
          {{ $t('Contact {exchange}', {
            exchange: transaction.exchange.providerInfo.name,
          }) }}
        </MwButton>
        <MwPoweredBy :powered="transaction.exchange.providerInfo" />
      </template>
      <MwButton
        v-else-if="transaction.rbf"
        type="primary"
        :isLoading="isLoading"
        @click="accelerate"
      >
        {{ $t('Accelerate') }}
      </MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__header {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--spacing-2xs);
      text-align: center;
    }

    &__amount-crypto {
      @include text-lg;
      @include ellipsis;
      width: 100%;

      &--positive {
        color: var(--color-primary);
      }

      &--negative {
        color: var(--color-danger);
      }
    }

    &__amount-fiat {
      @include text-sm;
      @include ellipsis;
      width: 100%;
      color: var(--color-secondary);
    }

    &__status {
      @include text-sm;
      color: var(--color-secondary);

      &--danger {
        color: var(--color-danger);
      }
    }

    &__arrow {
      width: var(--spacing-xl);
      height: var(--spacing-xl);

      &--up {
        transform: rotate(180deg);
      }
    }

    &__accept {
      display: flex;
      justify-content: center;
    }

    &__info {
      flex-grow: 1;
    }
  }
</style>
