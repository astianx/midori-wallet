<script>
import {
  Amount,
  errors,
} from '../../lib/account/domain.js';

import { WalletState } from '../../lib/account/domain.js';

import MwButton from '../../components/MwButton.vue';
import MwCryptoLogo from '../../components/MwCryptoLogo.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormDropdown from '../../components/MwForm/MwFormDropdown.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwStep from '../../components/MwStep.vue';
import MainLayout from '../../layouts/MainLayout.vue';

import { onShowOnHide } from '../../lib/mixins.js';
import {
  CardBigAmountError,
  CardNoFundsError,
  CardSmallAmountError,
} from '../../lib/account/Cards.js';

export default {
  components: {
    MainLayout,
    MwFormAmountInput,
    MwFormDropdown,
    MwButton,
    MwCryptoLogo,
    MwFormGroup,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  async onShow() {
    this.isLoading = true;
    this.errors = {};
    const wallet = this.$account.wallet(this.crypto._id);

    this.updateStorage({
      // cache price for all steps
      price: await this.$account.market.getPrice(this.crypto._id, this.$currency),
      pricePlatform: await this.$account.market.getPrice(this.platform._id, this.$currency),
      priceUSD: wallet?.isCsFeeSupported ?
        await this.$account.market.getPrice(wallet.crypto._id, 'USD') : undefined,
      address: this.$account.cards.getDepositAddress(this.crypto._id),
      gasLimit: wallet?.isGasLimitSupported ? wallet.gasLimit : undefined,
      feeRate: wallet?.isFeeRatesSupported ? WalletState.FEE_RATE_DEFAULT : undefined,
      crypto: this.crypto,
      platform: this.platform,
    });
    if (wallet) {
      if (![WalletState.STATE_LOADED, WalletState.STATE_LOADING].includes(wallet.state)) {
        await wallet.cleanup();
        await wallet.load();
      }
      if (wallet.isFeeRatesSupported) await wallet.loadFeeRates();
    }
    this.isLoading = false;
  },
  data() {
    return {
      isLoading: false,
      isLoadingMaxAmount: false,
      amount: undefined,
      errors: {},
    };
  },
  computed: {
    crypto() {
      return this.storage.crypto || this.$account.cryptoDB.get('tether@ethereum');
    },
    platform() {
      return this.storage.platform || this.$account.cryptoDB.platform('ethereum');
    },
  },
  methods: {
    async setMaxAmount() {
      this.isLoadingMaxAmount = true;
      try {
        const wallet = this.$account.wallet(this.crypto._id);
        if (!wallet) {
          return this.amount = new Amount(0n, this.crypto.decimals);
        }
        this.amount = await wallet.estimateMaxAmount({
          address: this.storage.address,
          feeRate: this.storage.feeRate,
          gasLimit: this.storage.gasLimit,
          price: this.storage.priceUSD,
        });
      } catch (err) {
        this.handleError(err);
      } finally {
        this.isLoadingMaxAmount = false;
      }
    },
    async confirm() {
      this.isLoading = true;
      this.errors = {};
      try {
        const wallet = this.$account.wallet(this.crypto._id);
        if (!wallet) throw new CardNoFundsError();

        const amount = this.amount || new Amount(0n, this.crypto.decimals);
        this.$account.cards.validateTopupAmount(amount, this.storage.card.product);

        await wallet.validateAmount({
          address: this.storage.address,
          feeRate: this.storage.feeRate,
          gasLimit: this.storage.gasLimit,
          amount,
          price: this.storage.priceUSD,
        });
        const fee = await wallet.estimateTransactionFee({
          address: this.storage.address,
          feeRate: this.storage.feeRate,
          gasLimit: this.storage.gasLimit,
          amount,
          price: this.storage.priceUSD,
        });

        const topup = this.$account.cards.estimateTopupAmount(amount, this.storage.card.product);

        this.updateStorage({
          amount,
          fee,
          topup,
          cardAction: 'topup',
        });
        this.next('confirm');
      } catch (err) {
        this.handleError(err);
      } finally {
        this.isLoading = false;
      }
    },
    handleError(err) {
      if (err instanceof CardNoFundsError) {
        this.errors['from'] = this.$t('No funds. Buy or receive {symbol} to continue.', {
          symbol: this.crypto.symbol,
        });
        return;
      }

      if (err instanceof CardSmallAmountError) {
        this.errors['amount'] = this.$t('Value is too small, minimum {amount} {symbol}', {
          amount: err.amount,
          symbol: this.crypto.symbol,
        });
        return;
      }
      if (err instanceof CardBigAmountError) {
        this.errors['amount'] = this.$t('Value is too big, maximum {amount} {symbol} (incl. fee)', {
          amount: err.amount,
          symbol: this.crypto.symbol,
        });
        return;
      }

      if (err instanceof errors.SmallAmountError) {
        this.errors['amount'] = this.$t('Value is too small, minimum {amount} {symbol}', {
          amount: err.amount,
          symbol: this.crypto.symbol,
        });
        return;
      }
      if (err instanceof errors.BigAmountError) {
        this.errors['amount'] = this.$t('Value is too big, maximum {amount} {symbol} (incl. fee)', {
          amount: err.amount,
          symbol: this.crypto.symbol,
        });
        return;
      }
      if (err instanceof errors.BigAmountConfirmationPendingError) {

        this.errors['amount'] = this.$t('Some funds are temporarily unavailable. To send this transaction, you will need to wait for your pending transactions to be confirmed first. Available {amount} {symbol}', {
          amount: err.amount,
          symbol: this.crypto.symbol,
        });
        return;
      }
      if (err instanceof errors.InsufficientCoinForTransactionFeeError) {
        this.errors['amount'] = this.$t('Insufficient funds to pay the transaction fee. Required {amount} {symbol}', {
          amount: err.amount,
          symbol: this.platform.symbol,
        });
        return;
      }
      console.error('Midori Wallet operation failed');
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Top up')">
    <MwFormGroup class="&__content">
      <MwFormDropdown
        label="From"
        :error="errors['from']"
        :value="crypto.name"
        @click="next('source')"
      >
        <template #before>
          <MwCryptoLogo
            :crypto="crypto"
            :platform="platform"
          />
        </template>
      </MwFormDropdown>
      <MwFormAmountInput
        v-model="amount"
        :label="$t('Amount')"
        :error="errors['amount']"
        :decimals="crypto.decimals"
        :symbol="crypto.symbol"
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
