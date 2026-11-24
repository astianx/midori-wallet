<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwFormGroup, MwFormTextareaReadonly },
  extends: MwStep,
  mixins: [onShowOnHide],
  data() { return { review: undefined, error: '', isLoading: false }; },
  async onShow() {
    try { this.review = (await this.$account.walletConnect()).reviewProposal(this.storage.proposal); }
    catch (error) { this.error = this.$t(error.message); }
  },
  methods: {
    async approve() {
      if (this.isLoading || !this.review) return;
      this.isLoading = true;
      try {
        const session = await (await this.$account.walletConnect()).approveSession(this.storage.proposal);
        this.updateStorage({ session, proposal: undefined }); this.replace('main');
      } catch (error) { this.error = this.$t(error.message); }
      finally { this.isLoading = false; }
    },
    async reject() {
      if (this.isLoading) return; this.isLoading = true;
      try { await (await this.$account.walletConnect()).rejectProposal(this.storage.proposal); }
      catch { this.error = this.$t('Connection unavailable. Try again or use Help.'); }
      finally { this.updateStorage({ proposal: undefined }); this.isLoading = false; this.back(); }
    },
  },
};
</script>
<template>
  <MainLayout :title="$t('Review connection')" :onBack="reject" allowReadFailure>
    <MwFormGroup v-if="review">
      <p>{{ $t('Connecting does not send money or sign a message.') }}</p>
      <p>{{ $t('{name} wants to connect to your wallet.', { name: review.name }) }}</p>
      <MwFormTextareaReadonly :label="$t('URL')" :value="review.url" />
      <p v-if="!review.verified">{{ $t('This domain has not been verified. Check the address before connecting.') }}</p>
      <MwFormTextareaReadonly :label="$t('Wallet address')" :value="review.address" />
      <MwFormTextareaReadonly :label="$t('Network')" value="Base" />
      <p>{{ $t('Connection permissions') }}</p>
      <ul>
        <li>{{ $t('View your wallet address') }}</li>
        <li v-if="review.namespaces.eip155.methods.some(method => method.includes('sign'))">{{ $t('Request message signatures') }}</li>
        <li v-if="review.namespaces.eip155.methods.includes('eth_sendTransaction')">{{ $t('Request USDC or bridged USDT transfers on Base') }}</li>
      </ul>
      <p>{{ $t('Every signature and payment requires your approval and PIN.') }}</p>
    </MwFormGroup>
    <p v-if="error" role="alert">{{ error }}</p>
    <MwButtonGroup>
      <MwButton type="primary" :isLoading="isLoading" :disabled="!review || !!error" @click="approve">{{ $t('Approve connection') }}</MwButton>
      <MwButton type="primary-link" :disabled="isLoading" @click="reject">{{ $t('Reject') }}</MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>
