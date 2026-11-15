<script>
import CryptoSendLayout from './CryptoSendLayout.vue';
import MwStep from '../../../components/MwStep.vue';
import MwButton from '../../../components/MwButton.vue';
import MwAppIcon from '../../../components/MwAppIcon.vue';
import { onShowOnHide } from '../../../lib/mixins.js';
import { Amount } from '../../../lib/account/domain.js';
export default {
  components: { CryptoSendLayout, MwButton, MwAppIcon }, extends: MwStep, mixins: [onShowOnHide],
  data() { return { intent: this.storage.intent, disconnected: false, stopped: true, checking: false, cancelling: false, generation: 0, timer: null }; },
  computed: {
    terminal() { return ['confirmed','reverted','expired','cancelled','rejected'].includes(this.intent?.status); },
    notSent() { return ['expired','cancelled','rejected'].includes(this.intent?.status); },
    receiptAvailable() { return !!(this.intent?.userOperationHash || this.intent?.transactionHash); },
    title() { return this.$t(this.disconnected ? 'Confirming payment' : this.intent?.status === 'confirmed' ? 'Payment sent' : this.intent?.status === 'reverted' ? 'Payment reverted' : this.notSent ? 'Payment not sent' : this.intent?.status === 'reorganized' ? 'Reorganized' : 'Confirming payment'); },
    message() {
      if (this.disconnected || this.intent?.status === 'ambiguous') return this.$t('We are checking this payment. Do not send it again.');
      if (this.intent?.status === 'reorganized') return this.$t('The network is updating this payment. We will keep checking it.');
      if (this.intent?.status === 'reverted') return this.$t('The recipient did not receive this payment. The network may have charged a cost.');
      return this.$t(this.intent?.status === 'confirmed' ? 'The recipient received the approved amount.' : this.notSent ? 'You can review a new payment.' : 'You can close this screen. This payment will stay in your history.');
    },
    amount() { return this.intent ? `${new Amount(this.intent.amountAtomic,6)} ${this.$wallet.crypto.symbol}` : '—'; },
    tone() { return this.disconnected ? 'waiting' : this.intent?.status === 'confirmed' ? 'success' : this.intent?.status === 'reverted' ? 'error' : this.notSent ? 'neutral' : 'waiting'; },
  },
  onShow() { this.stopped = false; this.generation++; this.check(); },
  onHide() { this.stopped = true; this.generation++; this.checking = false; clearTimeout(this.timer); },
  methods: {
    async check() {
      if (!this.intent?.id || this.stopped || this.$account.isLocked || this.checking || this.cancelling) return;
      clearTimeout(this.timer);
      const generation = this.generation, wallet = this.$wallet;
      this.checking = true;
      try {
        const intent = await wallet.payment(this.intent.id);
        if (this.stopped || generation !== this.generation) return;
        this.intent = intent; this.disconnected = false; this.updateStorage({ intent });
      } catch { if (!this.stopped && generation === this.generation) this.disconnected = true; }
      finally { if (generation === this.generation) this.checking = false; }
      if (this.stopped || generation !== this.generation) return;
      if (!this.terminal || this.disconnected) this.timer = setTimeout(() => this.check(),1500);
      else this.$account.refreshBalances();
    },
    receipt() { if (this.receiptAvailable) this.$router.push({ name: 'crypto.history', params: { cryptoId: this.$wallet.crypto._id }, query: { payment: this.intent.id } }); },
    async cancel() {
      if (this.cancelling || this.checking || !['preparing','quoted'].includes(this.intent?.status)) return;
      clearTimeout(this.timer); const generation = ++this.generation; this.cancelling = true;
      try { const intent = await this.$wallet.cancelPayment(this.intent.id); if (generation !== this.generation || this.stopped) return; this.intent = intent; this.updateStorage({ intent }); }
      catch { if (generation === this.generation) this.disconnected = true; }
      finally { this.cancelling = false; }
      if (!this.stopped) this.check();
    },
  },
};
</script>
<template>
  <CryptoSendLayout class="&" :title="title" :current="4" :storage="storage" @back="$router.push({ name: 'home' })">
    <div class="&__result" :data-payment-state="intent?.status" :data-tone="tone"><span class="&__icon"><MwAppIcon :name="tone === 'success' ? 'check' : tone === 'neutral' || tone === 'error' ? 'help' : 'clock'" /></span><h1>{{ title }}</h1><p role="status">{{ message }}</p><strong dir="ltr">{{ amount }}</strong></div>
    <dl v-if="intent" class="&__details"><dt>{{ $t('Network') }}</dt><dd>Base</dd><template v-if="intent.transactionHash"><dt>{{ $t('Transaction ID') }}</dt><dd>{{ intent.transactionHash }}</dd></template></dl>
    <MwButton v-if="!terminal || disconnected" type="primary-link" :isLoading="checking" @click="check">{{ $t('Check payment status') }}</MwButton>
    <template #actions><MwButton v-if="receiptAvailable" type="primary" @click="receipt">{{ $t('View receipt') }}</MwButton><MwButton v-if="['preparing','quoted'].includes(intent?.status)" type="secondary" :disabled="checking" :isLoading="cancelling" @click="cancel">{{ $t('Cancel') }}</MwButton><MwButton type="secondary" @click="$router.push({ name: 'activity' })">{{ $t('Activity') }}</MwButton><MwButton type="primary-link" @click="$router.push({ name: 'home' })">{{ $t('Back to wallet') }}</MwButton></template>
  </CryptoSendLayout>
</template>
<style lang="scss">
/* Hallmark · design-system: design.md · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } { &__icon { display: grid; place-items: center; width: 3.5rem; height: 3.5rem; color: var(--color-warning); background: var(--color-warning-surface); border-radius: 50%; margin-bottom: var(--space-lg); } &__icon svg { width: 1.75rem; height: 1.75rem; } [data-tone='success'] &__icon { background: var(--color-surface-tint); color: var(--color-accent); } [data-tone='error'] &__icon { background: var(--color-error-surface); color: var(--color-error); } [data-tone='neutral'] &__icon { background: var(--color-surface-subtle); color: var(--color-muted); } &__result h1 { margin: 0; font-size: var(--text-title); font-weight: 600; } &__result p { font-size: 0.9375rem; color: var(--color-muted); line-height: 1.5; } &__result strong { display: block; margin-top: var(--space-lg); font-size: 1.75rem; font-weight: 600; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; } &__details { margin: 0; font-size: 0.875rem; } &__details dt { color: var(--color-muted); margin-top: var(--space-sm); } &__details dd { margin: var(--space-xs) 0 0; overflow-wrap: anywhere; } }
</style>
