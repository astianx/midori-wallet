<script>
import CryptoSendLayout from './CryptoSendLayout.vue';
import MwPin from '../../../components/MwPin.vue';
import MwButton from '../../../components/MwButton.vue';
import MwStep from '../../../components/MwStep.vue';
import { Amount } from '../../../lib/account/domain.js';
import { onShowOnHide } from '../../../lib/mixins.js';
export default {
  components: { CryptoSendLayout, MwPin, MwButton }, extends: MwStep, mixins: [onShowOnHide],
  data() { return { now: Date.now(), timer: null }; },
  computed: {
    approvedIntent() { try { return JSON.parse(this.args?.approved); } catch { return undefined; } },
    debit() { const intent = this.approvedIntent; if (!intent || intent.feeMode === 'token' && !/^[0-9]+$/.test(intent.quote?.maxFeeAtomic)) return '—'; return `${new Amount(BigInt(intent.amountAtomic) + (intent.feeMode === 'token' ? BigInt(intent.quote.maxFeeAtomic) : 0n), 6)} ${this.$wallet.crypto.symbol}`; },
    cost() { const intent = this.approvedIntent; return intent?.feeMode === 'sponsored' ? this.$t('Sponsored by Midori') : /^[0-9]+$/.test(intent?.quote?.maxFeeAtomic) ? `${new Amount(intent.quote.maxFeeAtomic,6)} ${this.$wallet.crypto.symbol}` : '—'; },
    changed() { return !this.args?.approved || JSON.stringify(this.storage.intent) !== this.args.approved; },
    expired() { const until = Date.parse(this.storage.intent?.expiresAt); return !Number.isFinite(until) || this.now >= until || this.storage.intent?.status !== 'quoted'; },
    showPin() { return !this.changed && (!this.expired || !!this.$refs.pin?.isLoading); },
    amount() { return this.approvedIntent ? `${new Amount(this.approvedIntent.amountAtomic, 6)} ${this.$wallet.crypto.symbol}` : '—'; },
  },
  onShow() { this.now = Date.now(); this.timer = setInterval(() => { this.now = Date.now(); }, 150); },
  onHide() { clearInterval(this.timer); },
};
</script>
<template>
  <CryptoSendLayout class="&" :title="$t('Enter your PIN')" :current="3" :storage="storage" @back="back">
    <div class="&__summary"><small>{{ $t('Recipient receives') }} · Base</small><strong dir="ltr">{{ amount }}</strong><dl><div><dt>{{ $t('Midori fee') }}</dt><dd>0 {{ $wallet.crypto.symbol }}</dd></div><div><dt>{{ $t('Network cost') }}</dt><dd>{{ cost }}</dd></div><div><dt>{{ $t('Maximum total debit') }}</dt><dd>{{ debit }}</dd></div></dl><p>{{ $t('Every signature and payment requires your approval and PIN.') }}</p></div>
    <p v-if="!showPin" role="alert" class="&__error">{{ $t(changed ? 'The payment details changed. Review and confirm again.' : 'This quote expired. Review the payment again.') }}</p>
    <MwPin v-else ref="pin" mode="payment" :onSuccess="args.success" />
    <template v-if="!showPin" #actions><MwButton type="primary" @click="back">{{ $t('Review again') }}</MwButton></template>
  </CryptoSendLayout>
</template>
<style lang="scss">.#{ $filename } { &__summary small, &__summary p { color: var(--color-muted); font-size: var(--text-label); line-height: 1.5; } &__summary strong { display: block; margin-block: var(--space-xs); font-size: 1.5rem; font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; } &__summary dl { margin: var(--space-md) 0; font-size: var(--text-label); } &__summary dl > div { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-xs); padding-block: var(--space-xs); } &__summary dt { color: var(--color-muted); } &__summary dd { margin: 0; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; } &__error { color: var(--color-error); font-size: 0.875rem; } .mw-pin__dots { min-height: 3rem; } }</style>
