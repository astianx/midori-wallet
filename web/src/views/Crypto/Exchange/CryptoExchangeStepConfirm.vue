<script>
import { WalletState } from '../../../lib/account/domain.js';

import MwStep from '../../../components/MwStep.vue';
import MwTransactionConfirm from '../../../components/MwTransactionConfirm.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { walletSeed } from '../../../lib/mixins.js';
import BaseExchange, {
  InternalExchangeError,
} from '../../../lib/exchanges/BaseExchange.js';

export default {
  components: {
    MainLayout,
    MwTransactionConfirm,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
    };
  },
  methods: {
    async confirm() {
      this.isLoading = true;
      await this.walletSeed(async (walletSeed) => {
        try {
          const exchange = await this.$account.exchanges.createExchange({
            provider: this.storage.provider,
            from: this.$wallet.crypto._id,
            to: this.storage.to.crypto._id,
            amount: this.storage.amount,
            address: this.storage.address === 'your wallet'
              ? this.$account.wallet(this.storage.to.crypto._id).address
              : this.storage.address,
            extraId: (this.storage.address !== 'your wallet'
              && BaseExchange.EXTRA_ID.includes(this.storage.to.crypto._id))
              ? this.storage.extraId : undefined,
            refundAddress: this.$wallet.address,
          });
          let { depositAddress } = exchange;
          if (this.$wallet.isUnaliasSupported) {
            const data = await this.$wallet.unalias(depositAddress);
            if (data) depositAddress = data.address;
          }
          const options = {
            address: depositAddress,
            amount: this.storage.amount,
            price: this.storage.priceUSD,
          };
          if (this.$wallet.isFeeRatesSupported) {
            options.feeRate = WalletState.FEE_RATE_DEFAULT;
          }
          if (this.$wallet.isGasLimitSupported) {
            options.gasLimit = this.$wallet.gasLimit;
          }
          if (this.$wallet.crypto._id === 'xrp@ripple') {
            options.meta = { destinationTag: exchange.extraId };
          }
          if (this.$wallet.crypto._id === 'stellar@stellar') {
            options.meta = { memo: exchange.extraId };
          }
          const id = await this.$wallet.createTransaction(options, walletSeed);
          this.$account.emit('update');
          this.updateStorage({ status: true });
          await this.$account.exchanges.saveExchange({
            provider: this.storage.provider,
            from: this.$wallet.crypto._id,
            to: this.storage.to.crypto._id,
            transactionId: id,
            exchangeId: exchange.id,
            internal: this.storage.address === 'your wallet',
          });
        } catch (err) {
          if (err instanceof InternalExchangeError) {
            this.updateStorage({ status: false, message: this.$t('{exchange} error. Please try again later.', {
              exchange: this.$account.exchanges.getProviderInfo(this.storage.provider).name,
            }) });
            return;
          }
          this.updateStorage({ status: false });
          console.error('Midori Wallet operation failed');
        } finally {
          this.next('status');
        }
      });
      this.isLoading = false;
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Confirm swap')">
    <MwTransactionConfirm
      :transaction="storage"
      :powered="$account.exchanges.getProviderInfo(storage.provider)"
      :isLoading="isLoading"
      @confirm="confirm"
    />
  </MainLayout>
</template>
