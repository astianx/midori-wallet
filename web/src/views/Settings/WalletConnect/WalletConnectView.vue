<script>
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
    MwSteps,
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
    try {
      const walletConnect = await this.$account.walletConnect();
      if (!this.active) return;
      this.$options.client = walletConnect;
      this.ready = true;
      walletConnect.on('disconnect', this.disconnect);
    } catch (error) { this.error = this.$t(error.message); }
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
  methods: {
    disconnect() {
      this.$router.replace({ name: 'settings.walletconnect', force: true });
    },
  },
};
</script>

<template>
  <MwSteps
    v-if="ready && !error"
    :steps="$options.steps"
    :initialStorage="storage"
  />
  <p v-else role="alert">{{ error }}</p>
</template>
