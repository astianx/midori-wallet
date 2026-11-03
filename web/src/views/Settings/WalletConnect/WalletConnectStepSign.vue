<script>
import MwButton from '../../../components/MwButton.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { walletSeed } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormGroup,
    MwFormTextareaReadonly,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
    };
  },
  computed: {
    message() {
      return ['eth_signTypedData', 'eth_signTypedData_v4'].includes(this.storage.method)
        ? JSON.stringify(this.storage.data)
        : this.storage.data;
    },
  },
  methods: {
    async confirm() {
      this.isLoading = true;
      const walletConnect = await this.$account.walletConnect();
      await this.walletSeed(async (walletSeed) => {
        try {
          const { params } = this.storage.request;
          const wallet = this.$account.walletByChainId(params?.chainId);
          if (!wallet) {
            throw new Error(`Unknown wallet chainId: ${params?.chainId}`);
          }
          const data = this.storage.method === 'personal_sign'
            ? params.request.params[0]
            : params.request.params[1];
          const sign = ['eth_signTypedData', 'eth_signTypedData_v4'].includes(this.storage.method)
            ? await wallet.eth_signTypedData(data, walletSeed)
            : await wallet.eth_sign(data, walletSeed);
          await walletConnect.resolveSessionRequest(this.storage.request, sign);
          this.$account.emit('update');
          this.updateStorage({ status: true });
        } catch (err) {
          await walletConnect.rejectSessionRequest(this.storage.request, err.message);
          this.updateStorage({ status: false });
          console.error('Midori Wallet operation failed');
        } finally {
          this.next('status');
        }
      });
      this.isLoading = false;
    },
    async reject() {
      this.isLoading = true;
      try {
        const walletConnect = await this.$account.walletConnect();
        await walletConnect.rejectSessionRequest(this.storage.request);
      } catch (err) {
        console.error('Midori Wallet operation failed');
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
    :title="$t('Signature request')"
    @back="reject"
  >
    <MwFormGroup class="&__container">
      <div>{{ $t('Sign this message only if you completely understand it.') }}</div>
      <MwFormTextareaReadonly
        :value="message"
        :label="$t('Message')"
      />
    </MwFormGroup>
    <MwButton
      type="primary"
      :isLoading="isLoading"
      @click="confirm"
    >
      {{ $t('Confirm') }}
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
