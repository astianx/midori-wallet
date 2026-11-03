<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import * as MoneroErrors from '../../../lib/ui-runtime/engine-errors.js';
import { walletSeed } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwFormGroup,
    MwFormInput,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
      txId: '',
      error: undefined,
    };
  },
  methods: {
    async confirm() {
      this.isLoading = true;
      this.error = undefined;
      try {
        await this.$wallet.validateTransaction(this.txId);
        await this.walletSeed(async (walletSeed) => {
          try {
            await this.$wallet.addTransaction(this.txId, walletSeed);
            this.$account.emit('update');
            this.updateStorage({ status: true });
          } catch (err) {
            this.updateStorage({ status: false, error: err });
          }
          this.next('moneroStatus');
        });
      } catch (err) {
        if (err instanceof MoneroErrors.InvalidTransactionIDError) {
          return this.error = this.$t('Invalid transaction ID');
        }
        if (err instanceof MoneroErrors.TransactionAlreadyAddedError) {
          return this.error = this.$t('Transaction has already been accepted');
        }
        if (err instanceof MoneroErrors.UnknownTransactionError) {
          // eslint-disable-next-line max-len
          return this.error = this.$t('Transaction not found or has less than 10 confirmations. Please wait 20 minutes and try again.');
        }
        this.error = this.$account.unknownError(err);
        console.error(err);
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Accept transaction')">
    <MwFormGroup class="&__container">
      <MwFormInput
        v-model="txId"
        :label="$t('Transaction ID')"
        :error="error"
        clear
        :info="$t('Accept transaction')"
        @update:modelValue="error = undefined"
      >
        <template #info>
          <div>
            {{ $t('Only transactions addressed to you can be accepted. The minimum number of confirmations is 10.') }}
          </div>
        </template>
        <template #infoFooter>
          <MwButtonGroup>
            <MwButton
              type="primary-link"
              @click="$safeOpen('https://astian.org/midori-wallet/')"
            >
              {{ $t('Read more') }}
            </MwButton>
          </MwButtonGroup>
        </template>
      </MwFormInput>
    </MwFormGroup>
    <MwButton
      type="primary"
      :isLoading="isLoading"
      @click="confirm"
    >
      {{ $t('Accept') }}
    </MwButton>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      flex-grow: 1;
    }
  }
</style>
