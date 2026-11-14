<script>
import MwStep from '../../../components/MwStep.vue';
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import { Amount } from '../../../lib/account/domain.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwFormGroup, MwFormTextareaReadonly },
  extends: MwStep,
  mixins: [onShowOnHide],
  data() { return { error: '', isLoading: false, now: Date.now(), active: false }; },
  computed: {
    intent() { return this.storage.intent; },
    expired() { return !this.intent?.quote || this.now >= Date.parse(this.intent.expiresAt); },
    submitted() { return this.intent && this.intent.status !== 'quoted'; },
  },
  async onShow() {
    this.active = true;
    this.$options.timer = setInterval(() => {
      this.now = Date.now();
      if (this.active && this.submitted && !this.isLoading && this.now % 5000 < 1000 && !['expired', 'cancelled', 'rejected', 'reverted'].includes(this.intent.status)) void this.review();
    }, 1000);
    await this.review();
  },
  onHide() { this.active = false; clearInterval(this.$options.timer); },
  methods: {
    formatAmount(value) { return new Amount(value, 6).toString(); },
    async review() {
      this.isLoading = true; this.error = '';
      try {
        const client = await this.$account.walletConnect();
        const intent = await client.reviewTransfer(this.storage.request);
        if (!this.active) return;
        this.updateStorage({ intent });
        if (intent.transactionHash) await this.complete(intent.transactionHash);
        else if (['expired', 'cancelled', 'rejected', 'reverted'].includes(intent.status)) {
          this.error = this.$t('Payment not sent');
          await client.rejectSessionRequest(this.storage.request, 'Payment not sent');
        }
      } catch (error) { this.error = paymentError(error, this.$t, { symbol: this.storage.transfer.symbol }); }
      finally { this.isLoading = false; }
    },
    confirm() {
      if (this.expired || this.isLoading || this.submitted) return;
      const approved = JSON.stringify(this.intent);
      const request = JSON.stringify(this.storage.request);
      this.next('pin', { layout: 'MainLayout', mode: 'payment', success: async (_, pin, pinActive) => {
        try {
          const client = await this.$account.walletConnect();
          client.validateRequest(JSON.parse(request));
          const intent = await this.$account.authorizePayment(JSON.parse(approved), pin, this.storage.transfer.contract, () => pinActive() && this.$route.name === 'settings.walletconnect' && JSON.stringify(this.storage.request) === request && JSON.stringify(this.storage.intent) === approved);
          this.updateStorage({ intent }); this.backTo('confirm');
          if (intent.transactionHash) await this.complete(intent.transactionHash);
        } catch (error) {
          if (error.status === 401 || error.status === 429) throw error;
          this.error = paymentError(error, this.$t, { symbol: this.storage.transfer.symbol }); this.backTo('confirm');
        }
      } });
    },
    async complete(hash) {
      if (!/^0x[0-9a-fA-F]{64}$/.test(hash)) throw new Error('Invalid transaction hash');
      await (await this.$account.walletConnect()).resolveSessionRequest(this.storage.request, hash);
      this.$account.emit('update');
      this.updateStorage({ status: true, transaction: { url: `https://basescan.org/tx/${hash}` } });
      this.next('status');
    },
    async reject() {
      if (this.isLoading) return;
      const client = await this.$account.walletConnect();
      if (this.intent?.status === 'quoted') {
        const wallet = this.$account.wallets().find(wallet => wallet.crypto.address === this.storage.transfer.contract);
        try { await wallet.cancelPayment(this.intent.id); }
        catch (error) { this.error = paymentError(error, this.$t); return; }
      }
      await client.rejectSessionRequest(this.storage.request, this.submitted ? 'Payment tracking required in Midori' : 'User rejected');
      this.backTo('main');
    },
  },
};
</script>
<template>
  <MainLayout :title="$t('Review payment')" :onBack="reject" allowReadFailure>
    <MwFormGroup>
      <MwFormTextareaReadonly :label="$t('URL')" :value="storage.session?.peer.metadata.url" />
      <MwFormTextareaReadonly :label="$t('Recipient receives')" :value="`${formatAmount(storage.transfer.amountAtomic)} ${storage.transfer.symbol}`" />
      <MwFormTextareaReadonly :label="$t('To')" :value="storage.transfer.recipient" />
      <MwFormTextareaReadonly :label="$t('Network')" value="Base" />
      <template v-if="intent?.quote">
        <MwFormTextareaReadonly :label="$t('Midori fee')" value="0" />
        <MwFormTextareaReadonly :label="$t('Network cost')" :value="intent.feeMode === 'sponsored' ? $t('Sponsored by Midori') : `${formatAmount(intent.quote.estimatedFeeAtomic)} ${storage.transfer.symbol}`" />
        <p>{{ $t('Maximum network cost: {amount} {symbol}', { amount: formatAmount(intent.quote.maxFeeAtomic), symbol: storage.transfer.symbol }) }}</p>
        <p v-if="expired && !submitted">{{ $t('This quote expired. Review the payment again.') }}</p>
      </template>
      <p v-if="submitted">{{ $t('This payment is already authorized. Keep checking its status.') }}</p>
    </MwFormGroup>
    <p v-if="error" role="alert">{{ error }}</p>
    <MwButtonGroup>
      <MwButton v-if="submitted || !intent" type="primary" :isLoading="isLoading" @click="review">{{ $t('Check payment status') }}</MwButton>
      <MwButton v-else type="primary" :isLoading="isLoading" :disabled="expired" @click="confirm">{{ $t('Confirm and send') }}</MwButton>
      <MwButton type="primary-link" :disabled="isLoading" @click="reject">{{ submitted ? $t('Done') : $t('Reject') }}</MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>
