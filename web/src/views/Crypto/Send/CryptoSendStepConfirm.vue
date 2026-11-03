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
  components: { MainLayout, MwButton, MwButtonGroup, MwFormGroup, MwFormTextareaReadonly }, extends: MwStep, mixins: [onShowOnHide],
  data() { return { now: Date.now(), error: '', isLoading: false }; },
  computed: {
    intent() { return this.storage.intent; },
    expired() { return !this.intent?.quote || this.now >= Date.parse(this.intent.expiresAt); },
    fee() { return this.intent?.quote ? new Amount(this.intent.quote.estimatedFeeAtomic, 6).toString() : ''; },
    maxFee() { return this.intent?.quote ? new Amount(this.intent.quote.maxFeeAtomic, 6).toString() : ''; },
    total() {
      if (!this.intent) return '';
      const amount = BigInt(this.intent.amountAtomic);
      if (this.intent.feeMode === 'sponsored') return `${new Amount(amount,6)} ${this.$wallet.crypto.symbol}`;
      return this.$wallet.crypto.symbol === 'USDT' ? `${new Amount(amount+BigInt(this.intent.quote.maxFeeAtomic),6)} USDT` : `${new Amount(amount,6)} USDC + ${this.maxFee} USDT`;
    },
  },
  onShow() { this.now = Date.now(); this.$options.timer = setInterval(() => { this.now = Date.now(); }, 1000); },
  onHide() { clearInterval(this.$options.timer); },
  methods: {
    formatAmount(value) { return new Amount(value, 6).toString(); },
    async edit() {
      try { const previous = this.intent; if (this.intent?.status === 'quoted') await this.$wallet.cancelPayment(this.intent.id); this.updateStorage({ intent: undefined, feeMode: previous.feeMode, temp: { address: previous.recipientTag ? `@${previous.recipientTag}` : previous.to, amount: new Amount(previous.amountAtomic, 6) } }); this.replace('index'); }
      catch (error) { this.error = paymentError(error, this.$t); }
    },
    confirm() {
      if (this.expired || this.isLoading) return;
      const approved = JSON.stringify(this.intent);
      this.next('pin', { layout: 'MainLayout', mode: 'payment', success: async (_, pin, pinActive) => {
        if (JSON.stringify(this.storage.intent) !== approved) throw { code: 'PAYMENT_CHANGED' };
        this.isLoading = true;
        try {
          const intent = await this.$account.authorizePayment(JSON.parse(approved), pin, this.$wallet.crypto.address, () => pinActive() && this.$route.name === 'crypto.send' && JSON.stringify(this.storage.intent) === approved);
          this.updateStorage({ intent }); this.next('status');
        } catch (error) {
          if (error.status === 401 || error.status === 429) throw error;
          this.error = paymentError(error, this.$t); this.backTo('confirm');
        } finally { this.isLoading = false; }
      } });
    },
  },
};
</script>
<template>
  <MainLayout :title="$t('Review payment')" :onBack="edit" allowReadFailure>
    <MwFormGroup v-if="intent">
      <MwFormTextareaReadonly :label="$t('Recipient receives')" :value="`${formatAmount(intent.amountAtomic)} ${$wallet.crypto.symbol}`" />
      <MwFormTextareaReadonly :label="$t('To')" :value="intent.recipientTag ? `@${intent.recipientTag}` : intent.to" />
      <details v-if="intent.recipientTag"><summary>{{ $t('View wallet address') }}</summary><MwFormTextareaReadonly :value="intent.to" :label="$t('Wallet address')" /></details>
      <MwFormTextareaReadonly :label="$t('Midori fee')" value="0" />
      <template v-if="intent.feeMode === 'token'">
        <MwFormTextareaReadonly :label="$t('Estimated network cost')" :value="`${fee} USDT`" />
        <p>{{ $t('Maximum network cost: {amount} USDT', { amount: maxFee }) }}</p>
      </template>
      <MwFormTextareaReadonly v-else :label="$t('Network cost')" :value="$t('Sponsored by Midori')" />
      <MwFormTextareaReadonly :label="$t('Maximum total debit')" :value="total" />
      <p v-if="expired" role="alert">{{ $t('This quote expired. Review the payment again.') }}</p>
      <p v-if="error" role="alert">{{ error }}</p>
    </MwFormGroup>
    <MwButtonGroup>
      <MwButton type="primary" :disabled="expired" :isLoading="isLoading" @click="confirm">{{ $t('Confirm and send') }}</MwButton>
      <MwButton type="primary-link" @click="edit">{{ expired ? $t('Review again') : $t('Edit payment') }}</MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>
