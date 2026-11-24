<script>
import MwButton from '../../../components/MwButton.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: { MainLayout, MwButton, MwFormGroup, MwFormTextareaReadonly },
  extends: MwStep,
  mixins: [onShowOnHide],
  data() { return { error: '', isLoading: false, active: false }; },
  async onShow() {
    this.active = true;
    try {
      const client = await this.$account.walletConnect();
      if (!this.active) return;
      this.$options.client = client;
      client.on('request', this.handleRequest);
      await client.getPendingSessionRequests();
    } catch (error) { this.error = this.$t(error.message); }
  },
  onHide() { this.active = false; this.$options.client?.off('request', this.handleRequest); },
  methods: {
    async disconnect() {
      this.isLoading = true;
      try { await (await this.$account.walletConnect()).disconnectSession(); }
      catch (error) { this.error = this.$t(error.message); }
      finally { this.isLoading = false; }
    },
    async handleRequest(event) {
      if (!this.active) return;
      const client = await this.$account.walletConnect();
      try {
        const request = client.validateRequest(event);
        this.updateStorage({ request: event, method: request.method, data: request.data, transfer: request.transfer, intent: undefined, status: undefined });
        this.next(request.method === 'eth_sendTransaction' ? 'confirm' : 'sign');
      } catch (error) {
        this.error = this.$t(error.message);
        await client.rejectSessionRequest(event, error.message, error.code);
      }
    },
  },
};
</script>
<template>
  <MainLayout :title="$t('WalletConnect')" allowReadFailure>
    <MwFormGroup>
      <p>{{ $t('Your wallet is connected to {name}.', { name: storage.session?.peer.metadata.name }) }}</p>
      <MwFormTextareaReadonly :value="storage.session?.peer.metadata.url" :label="$t('URL')" />
      <MwFormTextareaReadonly :value="$account.recoveryDescriptor.address" :label="$t('Wallet address')" />
      <p>{{ $t('Connecting does not send money or sign a message.') }}</p>
      <details><summary>{{ $t('Connection permissions') }}</summary><ul><li v-for="method in storage.session?.namespaces?.eip155?.methods || []" :key="method">{{ method }}</li></ul></details>
      <p>{{ $t('Keep Midori open to review requests from this app.') }}</p>
      <p>{{ $t('WalletConnect supports USDC and bridged USDT on Base only.') }}</p>
    </MwFormGroup>
    <p v-if="error" role="alert">{{ error }}</p>
    <MwButton type="secondary" :isLoading="isLoading" @click="disconnect">{{ $t('Disconnect') }}</MwButton>
  </MainLayout>
</template>
