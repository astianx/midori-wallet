<script>
import { errors } from '../../../lib/account/domain.js';

import MwButton from '../../../components/MwButton.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwLoader from '../../../components/MwLoader.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormGroup,
    MwFormTextareaReadonly,
    MwLoader,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  async onShow() {
    this.isLoading = true;
    this.error = undefined;
    try {
      const { staking, unstaking, readyForClaim } = await this.$wallet.pendingRequests();
      this.staking = staking;
      this.unstaking = unstaking;
      this.readyForClaim = readyForClaim;
    } catch (err) {
      this.error = this.$account.unknownError(err);
      console.error('Midori Wallet operation failed');
    } finally {
      this.isLoading = false;
    }
  },
  data() {
    return {
      isLoading: false,
      isClaiming: false,
      staking: undefined,
      unstaking: undefined,
      readyForClaim: undefined,
      error: undefined,
      errorClaim: undefined,
    };
  },
  methods: {
    async claim() {
      this.isClaiming = true;
      this.errorClaim = undefined;
      try {
        const amount = this.readyForClaim;
        await this.$wallet.validateClaim();
        const fee = await this.$wallet.estimateClaim();
        this.updateStorage({
          method: 'claim',
          title: this.$t('Confirm claim'),
          amount,
          address: 'your wallet',
          fee,
        });
        this.next('confirm');
      } catch (err) {
        this.handleError(err);
      } finally {
        this.isClaiming = false;
      }
    },
    handleError(err) {
      if (err instanceof errors.InsufficientCoinForTransactionFeeError) {
        this.errorClaim = this.$t('Insufficient funds to pay the transaction fee. Required {amount} {symbol}', {
          amount: err.amount,
          symbol: this.$wallet.platform.symbol,
        });
        return;
      }
      console.error('Midori Wallet operation failed');
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Pending requests')">
    <MwLoader v-if="isLoading" />
    <div
      v-else-if="error"
      class="&__error"
    >
      {{ error }}
    </div>
    <template v-else>
      <MwFormGroup class="&__content">
        <MwFormTextareaReadonly
          :label="$t('Staking')"
          :value="`${staking} ${$wallet.crypto.symbol}`"
        />
        <MwFormTextareaReadonly
          :label="$t('Unstaking')"
          :value="`${unstaking} ${$wallet.crypto.symbol}`"
        />
        <MwFormTextareaReadonly
          :label="$t('Ready for claim')"
          :value="`${readyForClaim} ${$wallet.crypto.symbol}`"
          :error="errorClaim"
        />
      </MwFormGroup>

      <MwButton
        v-if="readyForClaim?.value > 0n"
        type="primary"
        :isLoading="isClaiming"
        @click="claim"
      >
        {{ $t('Claim') }}
      </MwButton>
    </template>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__content {
      flex-grow: 1;
    }

    &__error {
      @include text-md;
    }
  }
</style>
