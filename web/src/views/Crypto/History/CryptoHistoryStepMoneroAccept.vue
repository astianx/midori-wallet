<script>
import MwLoader from '../../../components/MwLoader.vue';
import MwStep from '../../../components/MwStep.vue';
import { WalletState } from '../../../lib/account/domain.js';
import MainLayout from '../../../layouts/MainLayout.vue';

import { onShowOnHide } from '../../../lib/mixins.js';
import { walletSeed } from '../../../lib/mixins.js';

export default {
  components: {
    MwLoader,
    MainLayout,
  },
  extends: MwStep,
  mixins: [walletSeed, onShowOnHide],
  async onShow() {
    this.isLoading = true;
    try {
      const wallet = this.$account.wallet('monero@monero');
      if (![WalletState.STATE_LOADED, WalletState.STATE_LOADING].includes(wallet.state)) {
        await wallet.cleanup();
        await wallet.load();
      }
      const txId = this.storage.payoutHash;
      await wallet.validateTransaction(txId);
      await this.walletSeed(async (walletSeed) => {
        try {
          await wallet.addTransaction(txId, walletSeed);
          this.$account.emit('update');
          this.updateStorage({ status: true });
        } catch (err) {
          this.updateStorage({ status: false, error: err });
        }
        this.next('moneroStatus');
      });
    } catch (err) {
      this.updateStorage({ status: false, error: err });
      this.next('moneroStatus');
    } finally {
      this.isLoading = false;
    }
  },
  data() {
    return {
      isLoading: false,
    };
  },
};
</script>

<template>
  <MainLayout :title="$t('Accept transaction')">
    <MwLoader v-if="isLoading" />
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      align-items: center;
      justify-content: center;
    }

    &__icon {
      width: var(--spacing-9xl);
      height: var(--spacing-9xl);
    }

    &__info {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--spacing-2xs);
    }

    &__title {
      @include text-md;
      @include text-bold;
    }

    &__subtitle {
      @include text-sm;
    }
  }
</style>
