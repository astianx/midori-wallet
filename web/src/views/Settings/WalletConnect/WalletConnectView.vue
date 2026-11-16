<script>
import MainLayout from '../../../layouts/MainLayout.vue';
import MwButton from '../../../components/MwButton.vue';
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import WalletConnectStepConfirm from './WalletConnectStepConfirm.vue';
import WalletConnectStepProposal from './WalletConnectStepProposal.vue';
import WalletConnectStepIndex from './WalletConnectStepIndex.vue';
import WalletConnectStepMain from './WalletConnectStepMain.vue';
import WalletConnectStepQr from './WalletConnectStepQr.vue';
import WalletConnectStepSign from './WalletConnectStepSign.vue';
import WalletConnectStepStatus from './WalletConnectStepStatus.vue';

import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: {
    MwSteps, MainLayout, MwButton,
  },
  mixins: [onShowOnHide],
  data() {
    return {
      storage: { uri: this.$route.query.uri },
      error: '',
      active: false,
      ready: false,
    };
  },
  async onShow() {
    this.active = true;
    await this.load();
  },
  methods: {
    async load() { this.error = '';
    try {
      const walletConnect = await this.$account.walletConnect();
      if (!this.active) return;
      this.$options.client = walletConnect;
      this.ready = true;
      walletConnect.on('disconnect', this.disconnect);
    } catch (error) { this.error = this.$t(error.message); }
    },
    disconnect() { this.$router.replace({ name: 'settings.walletconnect', force: true }); },
  },
  async onHide() {
    this.active = false;
    this.$options.client?.off('disconnect', this.disconnect);
  },
  steps: {
    index: WalletConnectStepIndex,
    main: WalletConnectStepMain,
    confirm: WalletConnectStepConfirm,
    sign: WalletConnectStepSign,
    pin: MwPinStep,
    status: WalletConnectStepStatus,
    qr: WalletConnectStepQr,
    proposal: WalletConnectStepProposal,
  },
};
</script>

<template>
  <MwSteps
    v-if="ready && !error"
    :steps="$options.steps"
    :initialStorage="storage"
  />
  <MainLayout v-else :title="$t('Connections')"><p v-if="error" role="alert">{{ $t('Connection unavailable. Try again or use Help.') }}</p><p v-else role="status">{{ $t('Checking…') }}</p><MwButton v-if="error" type="secondary" @click="load">{{ $t('Refresh') }}</MwButton><MwButton type="primary-link" @click="$router.push({name:'settings.help'})">{{ $t('Help') }}</MwButton></MainLayout>
</template>
