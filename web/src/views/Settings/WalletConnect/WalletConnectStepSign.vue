<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwFormGroup, MwFormTextareaReadonly },
  extends: MwStep,
  data() { return { error: '' }; },
  computed: {
    message() {
      if (this.storage.method !== 'personal_sign') return JSON.stringify(this.storage.data, null, 2);
      const bytes = new Uint8Array((this.storage.data.slice(2).match(/../g) || []).map(byte => Number.parseInt(byte, 16)));
      try { return new TextDecoder('utf-8', { fatal: true }).decode(bytes); }
      catch { return this.storage.data; }
    },
  },
  methods: {
    confirm() {
      const approved = JSON.stringify(this.storage.request);
      this.next('pin', { layout: 'MainLayout', mode: 'payment', success: async (_, pin, pinActive) => {
        const client = await this.$account.walletConnect();
        try {
          const event = JSON.parse(approved);
          const signature = await this.$account.authorizeWalletConnect(event, pin, () => pinActive() && this.$route.name === 'settings.walletconnect' && JSON.stringify(this.storage.request) === approved);
          await client.resolveSessionRequest(event, signature);
          this.updateStorage({ status: true }); this.next('status');
        } catch (error) {
          if (error.status === 401 || error.status === 429) throw error;
          await client.rejectSessionRequest(JSON.parse(approved), error.message, error.code).catch(() => {});
          this.error = this.$t(error.message); this.backTo('sign');
        }
      } });
    },
    async reject() {
      try { await (await this.$account.walletConnect()).rejectSessionRequest(this.storage.request); }
      finally { this.back(); }
    },
  },
};
</script>
<template>
  <MainLayout :title="$t('Signature request')" :onBack="reject" allowReadFailure>
    <MwFormGroup>
      <MwFormTextareaReadonly :value="storage.session?.peer.metadata.url" :label="$t('URL')" />
      <p>{{ $t('Sign this message only if you completely understand it.') }}</p>
      <MwFormTextareaReadonly :value="message" :label="$t('Message')" />
      <details v-if="storage.method === 'personal_sign'"><summary>{{ $t('Message bytes') }}</summary><MwFormTextareaReadonly :value="storage.data" :label="$t('Message bytes')" /></details>
      <p>{{ $t('Smart account signatures require an app that supports contract wallets.') }}</p>
    </MwFormGroup>
    <p v-if="error" role="alert">{{ error }}</p>
    <MwButtonGroup>
      <MwButton type="primary" :disabled="!!error" @click="confirm">{{ $t('Confirm') }}</MwButton>
      <MwButton type="primary-link" @click="reject">{{ $t('Reject') }}</MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>
