<script>
import MwStep from '../../../components/MwStep.vue';
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import { onShowOnHide } from '../../../lib/mixins.js';
import { Amount } from '../../../lib/account/domain.js';
export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwFormTextareaReadonly }, extends: MwStep, mixins: [onShowOnHide],
  data() { return { intent: this.storage.intent, disconnected: false, stopped: false }; },
  computed: {
    title() { return this.$t(this.intent?.status === 'confirmed' ? 'Payment sent' : this.intent?.status === 'reverted' ? 'Payment reverted' : ['expired','cancelled','rejected'].includes(this.intent?.status) ? 'Payment not sent' : 'Confirming payment'); },
    message() {
      if (this.disconnected || this.intent?.status === 'ambiguous') return this.$t('We are checking this payment. Do not send it again.');
      if (this.intent?.status === 'reorganized') return this.$t('The network is updating this payment. We will keep checking it.');
      if (this.intent?.status === 'reverted') return this.$t('The recipient did not receive this payment. The network may have charged a cost.');
      return this.$t(this.intent?.status === 'confirmed' ? 'The recipient received the approved amount.' : ['expired','cancelled','rejected'].includes(this.intent?.status) ? 'You can review a new payment.' : 'You can close this screen. This payment will stay in your history.');
    },
    amount() { return this.intent ? `${new Amount(this.intent.amountAtomic,6)} ${this.$wallet.crypto.symbol}` : ''; },
  },
  onShow() { this.stopped = false; this.check(); },
  onHide() { this.stopped = true; clearTimeout(this.$options.timer); },
  methods: {
    async check() {
      if (!this.intent?.id || this.stopped || this.$account.isLocked) return;
      try { const intent = await this.$wallet.payment(this.intent.id); if (this.stopped) return; this.intent = intent; this.disconnected = false; this.updateStorage({ intent }); }
      catch { this.disconnected = true; }
      if (!this.stopped && !['confirmed','reverted','expired','cancelled','rejected'].includes(this.intent.status)) this.$options.timer = setTimeout(() => this.check(),1500);
      else this.$account.emit('update');
    },
    receipt() { this.$router.push({ name: 'crypto.history', params: { cryptoId: this.$wallet.crypto._id }, query: { payment: this.intent.id } }); },
    async cancel() { try { this.intent = await this.$wallet.cancelPayment(this.intent.id); this.updateStorage({ intent: this.intent }); } catch { this.disconnected = true; } },
  },
};
</script>
<template>
  <MainLayout :title="title" allowReadFailure>
    <p role="status">{{ message }}</p>
    <MwFormTextareaReadonly :label="$t('Amount')" :value="amount" />
    <MwFormTextareaReadonly v-if="intent?.transactionHash" :label="$t('Transaction ID')" :value="intent.transactionHash" />
    <MwButtonGroup>
      <MwButton v-if="intent && !['preparing','quoted'].includes(intent.status)" type="primary" @click="receipt">{{ $t('View receipt') }}</MwButton>
      <MwButton v-else type="primary-link" @click="cancel">{{ $t('Cancel') }}</MwButton>
      <MwButton type="primary-link" @click="$router.push({ name: 'crypto', params: { cryptoId: $wallet.crypto._id } })">{{ $t('Back to wallet') }}</MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>
