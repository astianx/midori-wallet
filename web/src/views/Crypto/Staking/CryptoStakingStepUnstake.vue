<script>
import { cryptoSubtitle } from '../../../lib/helpers.js';
import {
  Amount,
  errors,
} from '../../../lib/account/domain.js';

import MwButton from '../../../components/MwButton.vue';
import MwFormAmountInput from '../../../components/MwForm/MwFormAmountInput.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwFormAmountInput,
    MwButton,
    MwFormGroup,
    MwFormTextareaReadonly,
  },
  extends: MwStep,
  data() {
    return {
      isLoading: false,
      isLoadingMaxAmount: false,
      subtitle: cryptoSubtitle(this.$wallet),
      amountValue: undefined,
      error: undefined,
    };
  },
  methods: {
    async setMaxAmount() {
      this.isLoadingMaxAmount = true;
      try {
        this.amountValue = await this.$wallet.estimateUnstakeMaxAmount({ price: this.storage.priceUSD });
      } catch (err) {
        this.handleError(err);
      } finally {
        this.isLoadingMaxAmount = false;
      }
    },
    async confirm() {
      this.isLoading = true;
      this.error = undefined;
      try {
        const amount = this.amountValue || new Amount(0n, this.$wallet.crypto.decimals);
        await this.$wallet.validateUnstakeAmount({
          amount,
          price: this.storage.priceUSD,
        });
        const fee = await this.$wallet.estimateUnstake({
          amount,
          price: this.storage.priceUSD,
        });
        this.updateStorage({
          method: 'unstake',
          title: this.$t('Confirm unstake'),
          amount,
          address: 'your wallet',
          fee,
        });
        this.next('confirm');
      } catch (err) {
        this.handleError(err);
      } finally {
        this.isLoading = false;
      }
    },
    handleError(err) {
      if (err instanceof errors.SmallAmountError) {
        this.error = this.$t('Value is too small, minimum {amount} {symbol}', {
          amount: err.amount,
          symbol: this.$wallet.crypto.symbol,
        });
        return;
      }
      if (err instanceof errors.BigAmountError) {
        this.error = this.$t('Value is too big, maximum {amount} {symbol} (incl. fee)', {
          amount: err.amount,
          symbol: this.$wallet.crypto.symbol,
        });
        return;
      }
      if (err instanceof errors.InsufficientCoinForTransactionFeeError) {
        this.error = this.$t('Insufficient funds to pay the transaction fee. Required {amount} {symbol}', {
          amount: err.amount,
          symbol: this.$wallet.platform.symbol,
        });
        return;
      }
      console.error(err);
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Unstake {symbol}', { symbol: $wallet.crypto.symbol })"
    :description="subtitle"
  >
    <MwFormGroup class="&__content">
      <MwFormAmountInput
        v-model="amountValue"
        :label="$t('Amount')"
        :error="error"
        :decimals="$wallet.crypto.decimals"
        :symbol="$wallet.crypto.symbol"
        :factors="$wallet.isFactorsSupported ? $wallet.factors : []"
        :price="storage.price"
        :currency="$currency"
        @update:modelValue="error = undefined"
      />
      <MwButton
        type="secondary"
        :isLoading="isLoadingMaxAmount"
        @click="setMaxAmount"
      >
        {{ $t('max') }}
      </MwButton>
      <MwFormTextareaReadonly
        :label="$t('Unstaking period')"
        :info="$t('Unstaking period')"
        :value="`~${$duration($wallet.unstakingPeriod)}`"
        inheritDir
      >
        <template #info>
          <div>
            {{ $t('Approximate time before your funds become available for claim.') }}
          </div>
        </template>
      </MwFormTextareaReadonly>
    </MwFormGroup>

    <MwButton
      type="primary"
      :isLoading="isLoading"
      @click="confirm"
    >
      {{ $t('Continue') }}
    </MwButton>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__content {
      flex-grow: 1;
    }
  }
</style>
