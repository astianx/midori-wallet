<script>

import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwLoader from '../../../components/MwLoader.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import HistoryIcon from '../../../assets/svg/history.svg';
import ReceiveIcon from '../../../assets/svg/receive.svg';
import SendIcon from '../../../assets/svg/send.svg';

import { cryptoToFiat } from '../../../lib/helpers.js';

import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwFormGroup,
    MwFormTextareaReadonly,
    MwLoader,
    HistoryIcon,
    ReceiveIcon,
    SendIcon,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  data() {
    return {
      isLoading: true,
      staked: undefined,
      fiat: undefined,
      apr: undefined,
      error: undefined,
    };
  },
  computed: {
    supportUrl() {
      if (this.$wallet.crypto._id === 'ethereum@ethereum') {
        return 'https://github.com/astianx/midori-wallet/issues';
      }
    },
  },
  async onShow() {
    this.isLoading = true;
    this.error = undefined;
    await this.$wallet.cleanup();
    try {
      const { staked, apr } = await this.$wallet.staking();
      this.staked = staked;
      this.apr = apr;
      const price = await this.$account.market.getPrice(this.$wallet.crypto._id, this.$currency);
      const priceUSD = this.$wallet.isCsFeeSupported ?
        await this.$account.market.getPrice(this.$wallet.crypto._id, 'USD') : undefined;
      this.updateStorage({
        // cache price for all steps
        price,
        priceUSD,
        pricePlatform: price,
      });
      this.fiat = cryptoToFiat(
        this.staked,
        price,
        this.$wallet.crypto.decimals
      );
    } catch (err) {
      this.error = this.$account.unknownError(err);
      console.error('Midori Wallet operation failed');
    } finally {
      this.isLoading = false;
    }
  },
};
</script>

<template>
  <MainLayout :title="$t('Staking')">
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
          :label="$t('Staked')"
          :value="`${staked} ${$wallet.crypto.symbol}`"
        />
        <div>
          <span dir="ltr">
            {{ $fiatPrecise(fiat) }}
          </span>
        </div>
        <MwFormTextareaReadonly
          :label="$t('APR')"
          :value="$n(apr, 'percent', { signDisplay: 'never' })"
        />
        <MwButton
          type="primary-link"
          @click="$safeOpen(supportUrl)"
        >
          {{ $t('How it works?') }}
        </MwButton>
      </MwFormGroup>

      <MwButtonGroup
        class="&__actions"
        type="circle"
      >
        <MwButton
          type="circle"
          @click="next('stake')"
        >
          <template #circle>
            <SendIcon />
          </template>
          {{ $t('Stake') }}
        </MwButton>
        <MwButton
          v-if="staked?.value > 0n"
          type="circle"
          @click="next('unstake')"
        >
          <template #circle>
            <ReceiveIcon />
          </template>
          {{ $t('Unstake') }}
        </MwButton>
        <MwButton
          type="circle"
          @click="next('pendingRequests')"
        >
          <template #circle>
            <HistoryIcon />
          </template>
          {{ $t('Pending requests') }}
        </MwButton>
      </MwButtonGroup>
    </template>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__content {
      flex-grow: 1;
    }

    &__actions {
      position: relative;
      width: 100%;
      max-width: 25rem;
      align-self: center;
    }

    &__error {
      @include text-md;
    }
  }
</style>
