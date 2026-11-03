<script>
import MwButton from '../../../components/MwButton.vue';
import MwFormBigIntInput from '../../../components/MwForm/MwFormBigIntInput.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import * as EvmErrors from '../../../lib/ui-runtime/engine-errors.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormBigIntInput,
    MwFormGroup,
  },
  extends: MwStep,
  data() {
    return {
      isLoading: false,
      gasLimit: this.storage.gasLimit,
      error: undefined,
    };
  },
  methods: {
    async confirm() {
      this.isLoading = true;
      this.error = undefined;
      try {
        const { params } = this.storage.request;
        const wallet = this.$account.walletByChainId(params?.chainId);
        if (!wallet) {
          throw new Error(`Unknown wallet chainId: ${params?.chainId}`);
        }
        await wallet.validateGasLimit({ gasLimit: this.gasLimit });
        this.updateStorage({ gasLimit: this.gasLimit });

        this.storage.transaction.fee = await wallet.estimateTransactionFee({
          amount: this.storage.transaction.amount,
          address: params.request.params[0].to,
          gasLimit: this.gasLimit,
        });
        this.next('confirm');
      } catch (err) {
        if (err instanceof EvmErrors.SmallGasLimitError) {
          this.error = this.$t('Value is too small, minimum {value}', {
            value: err.value,
          });
          return;
        }
        if (err instanceof EvmErrors.BigGasLimitError) {
          this.error = this.$t('Value is too big, maximum {value}', {
            value: err.value,
          });
          return;
        }
        console.error(err);
      } finally {
        this.isLoading = false;
      }
    },
    async reject() {
      this.isLoading = true;
      try {
        const walletConnect = await this.$account.walletConnect();
        await walletConnect.rejectSessionRequest(this.storage.request);
      } catch (err) {
        console.error(err);
      } finally {
        this.isLoading = false;
        this.back();
      }
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('WalletConnect')"
    @back="reject"
  >
    <MwFormGroup class="&__gas">
      <MwFormBigIntInput
        v-model="gasLimit"
        :label="$t('Gas limit')"
        :error="error"
        :info="$t('Gas limit')"
        @update:modelValue="error = undefined"
      >
        <template #info>
          <div>
            <!-- eslint-disable-next-line max-len -->
            {{ $t('Gas limit is the amount of gas to send with your transaction. Increasing this number will not get your transaction confirmed faster.') }}
          </div>
        </template>
      </MwFormBigIntInput>
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

    &__gas {
      flex-grow: 1;
    }
  }
</style>
