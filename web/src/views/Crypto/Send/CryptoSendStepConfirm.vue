<script>
import { formatMidoriTag } from '../../../lib/tags.js';
import CryptoSendLayout from './CryptoSendLayout.vue';
import MwStep from '../../../components/MwStep.vue';
import MwButton from '../../../components/MwButton.vue';
import { Amount } from '../../../lib/account/domain.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
import { onShowOnHide } from '../../../lib/mixins.js';
export default {
  components: { CryptoSendLayout, MwButton }, extends: MwStep, mixins: [onShowOnHide],
  data() { return { now: Date.now(), error: '', isLoading: false, timer: null }; },
  computed: {
    intent() { return this.storage.intent; },
    expired() { const until = Date.parse(this.intent?.expiresAt); return this.intent?.status !== 'quoted' || !this.intent?.quote || !Number.isFinite(until) || this.now >= until; },
    knownFees() { return this.intent?.feeMode === 'sponsored' || [this.intent?.quote?.estimatedFeeAtomic, this.intent?.quote?.maxFeeAtomic].every(value => typeof value === 'string' && /^[0-9]+$/.test(value)); },
    total() { if (!this.intent || !this.knownFees) return '—'; return `${new Amount(BigInt(this.intent.amountAtomic) + (this.intent.feeMode === 'token' ? BigInt(this.intent.quote.maxFeeAtomic) : 0n), 6)} ${this.$wallet.crypto.symbol}`; },
    expiryText() { return Number.isFinite(Date.parse(this.intent?.expiresAt)) ? this.$t('Quote expires at {time}', { time: this.$d(new Date(this.intent.expiresAt), 'short') }) : ''; },
  },
  onShow() { this.now = Date.now(); this.timer = setInterval(() => { this.now = Date.now(); }, 250); },
  onHide() { clearInterval(this.timer); },
  methods: {
    formatMidoriTag,
    formatAmount(value) { return typeof value === 'string' && /^[0-9]+$/.test(value) ? new Amount(value, 6).toString() : '—'; },
    async edit(target = 'amount') {
      if (this.isLoading || !this.intent) return;
      this.isLoading = true; this.error = '';
      const previous = this.intent;
      try {
        if (previous.status === 'quoted') {
          const cancelled = await this.$wallet.cancelPayment(previous.id);
          if (!['cancelled','expired','rejected'].includes(cancelled.status)) { this.updateStorage({ intent: cancelled }); this.replace('status'); return; }
        }
        const amount = new Amount(previous.amountAtomic, 6);
        this.updateStorage({ intent: undefined, feeMode: previous.feeMode, amount, temp: { address: previous.recipientTag ? formatMidoriTag(previous.recipientTag) : previous.to, amount } });
        this.replace(target);
      } catch (error) {
        if (error.code === 'PAYMENT_TRACKING_REQUIRED') {
          try { const intent = await this.$wallet.payment(previous.id); this.updateStorage({ intent }); this.replace('status'); return; } catch { /* Keep the reviewed details until their state can be checked. */ }
        }
        this.error = paymentError(error, this.$t, { symbol: this.$wallet.crypto.symbol });
      } finally { this.isLoading = false; }
    },
    confirm() {
      if (this.expired || !this.knownFees || this.isLoading || Date.now() >= Date.parse(this.intent.expiresAt)) return;
      const approved = JSON.stringify(this.intent), cryptoId = this.$wallet.crypto._id;
      this.next('pin', { approved, success: async (_, pin, pinActive) => {
        const current = () => pinActive() && this.$route.name === 'crypto.send' && this.$route.params.cryptoId === cryptoId && JSON.stringify(this.storage.intent) === approved && Date.now() < Date.parse(JSON.parse(approved).expiresAt);
        if (!current()) throw { code: 'PAYMENT_CHANGED' };
        this.isLoading = true;
        try {
          const intent = await this.$account.authorizePayment(JSON.parse(approved), pin, this.$wallet.crypto.address, current);
          if (this.$account.isLocked || this.$route.name !== 'crypto.send' || this.$route.params.cryptoId !== cryptoId || this.storage.intent?.id !== intent.id) return;
          this.updateStorage({ intent }); this.next('status');
        } catch (error) {
          if (error.status === 401 || error.status === 429) throw error;
          if (!pinActive()) return;
          this.error = paymentError(error, this.$t, { symbol: this.$wallet.crypto.symbol }); this.backTo('confirm');
        } finally { this.isLoading = false; }
      } });
    },
  },
};
</script>
<template>
  <CryptoSendLayout class="&" :title="$t('Review payment')" :current="2" :storage="storage" @back="edit('amount')">
    <template v-if="intent"><div class="&__hero"><span>{{ $t('Recipient receives') }}</span><strong dir="ltr">{{ formatAmount(intent.amountAtomic) }} <small>{{ $wallet.crypto.symbol }}</small></strong></div><dl class="&__details"><div><dt>{{ $t('To') }}</dt><dd>{{ intent.recipientTag ? formatMidoriTag(intent.recipientTag) : intent.to }}</dd></div><div><dt>{{ $t('Network') }}</dt><dd>Base</dd></div><div><dt>{{ $t('Midori fee') }}</dt><dd>0 {{ $wallet.crypto.symbol }}</dd></div><template v-if="intent.feeMode === 'token'"><div><dt>{{ $t('Estimated network cost') }}</dt><dd>{{ formatAmount(intent.quote?.estimatedFeeAtomic) }} {{ $wallet.crypto.symbol }}</dd></div><div><dt>{{ $t('Network cost') }}</dt><dd>{{ $t('Maximum network cost: {amount} {symbol}', { amount: formatAmount(intent.quote?.maxFeeAtomic), symbol: $wallet.crypto.symbol }) }}</dd></div></template><div v-else><dt>{{ $t('Network cost') }}</dt><dd>{{ $t('Sponsored by Midori') }}</dd></div><div class="&__total"><dt>{{ $t('Maximum total debit') }}</dt><dd dir="ltr">{{ total }}</dd></div></dl><p class="&__expiry">{{ expiryText }}</p><div class="&__edit"><MwButton type="primary-link" small :disabled="isLoading" @click="edit('index')">{{ $t('Edit') }} · {{ $t('To') }}</MwButton><MwButton type="primary-link" small :disabled="isLoading" @click="edit('amount')">{{ $t('Edit') }} · {{ $t('Amount') }}</MwButton></div></template>
    <p v-if="expired" class="&__error" role="alert">{{ $t('This quote expired. Review the payment again.') }}</p><p v-else-if="!knownFees" class="&__error" role="alert">{{ $t('Could not calculate the network cost. No payment was sent.') }}</p><p v-if="error" class="&__error" role="alert">{{ error }}</p>
    <template #actions><MwButton type="primary" :disabled="expired || !knownFees" :isLoading="isLoading" @click="confirm">{{ $t('Confirm and send') }}</MwButton><MwButton v-if="expired" type="primary-link" :isLoading="isLoading" @click="edit('amount')">{{ $t('Review again') }}</MwButton></template>
  </CryptoSendLayout>
</template>
<style lang="scss">
/* Hallmark · design-system: design.md · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } {
  &__hero > span { display: block; color: var(--color-muted); font-size: var(--text-label); }
  &__hero strong { display: block; margin-top: var(--space-sm); font-size: clamp(1.75rem, 4vw, 2.5rem); font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; letter-spacing: -0.035em; }
  &__hero small { font-size: 1rem; font-weight: 500; }
  &__details { margin: 0; font-size: 0.875rem; }
  &__details > div { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: var(--space-xs) var(--space-md); padding-block: var(--space-sm); border-bottom: 1px solid var(--color-rule); }
  &__details dt { color: var(--color-muted); }
  &__details dd { margin: 0; max-width: 100%; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
  &__total { font-weight: 600; }
  &__expiry { font-size: var(--text-label); color: var(--color-muted); margin: 0; }
  &__edit { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
  &__edit > button { flex: 1 0 auto; }
  &__error { color: var(--color-error); font-size: 0.875rem; line-height: 1.5; }
}
</style>
