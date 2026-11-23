<script>
import MwNetworkSelector from '../../../components/MwNetworkSelector.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import MwButton from '../../../components/MwButton.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwFormAmountInput from '../../../components/MwForm/MwFormAmountInput.vue';
import MwPin from '../../../components/MwPin.vue';
import { Amount } from '../../../lib/account/domain.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
export default {
  components: { MwNetworkSelector, MainLayout, MwButton, MwFormInput, MwFormAmountInput, MwPin },
  data() { return { address: this.$route.query.address || '', amount: undefined, quote: null, approved: '', pin: false, busy: false, error: '', result: null, disposed: false, revision: 0, exchange: null, now: Date.now(), timer: null }; },
  computed: {
    expired() { return !this.quote || this.now >= Date.parse(this.quote.expiresAt); },
    total() { return this.quote ? new Amount(BigInt(this.quote.amountAtomic) + BigInt(this.quote.maxFeeAtomic), 8).toString() : '—'; },
  },
  async mounted() {
    this.timer = setInterval(() => { this.now = Date.now(); }, 500);
    try {
      if (this.$route.query.exchange) {
        this.busy = true; this.exchange = await this.$wallet.exchangeOrder(this.$route.query.exchange);
        if (this.disposed) return;
        if (this.exchange.depositHash) { this.result = { hash: this.exchange.depositHash, status: 'ambiguous' }; return; }
        if (this.exchange.status !== 'waiting' || !/^bitcoin$/i.test(this.exchange.quote.from.network)) throw { code: 'PAYMENT_CHANGED' };
        this.address = this.exchange.depositAddress; this.amount = Amount.fromString(this.exchange.quote.amountFrom, 8);
      } else {
        this.result = await this.$wallet.pendingBitcoin();
        if (this.$route.query.amount) this.amount = Amount.fromString(this.$route.query.amount, 8);
      }
    } catch (error) { this.error = paymentError(error, this.$t, { symbol: 'BTC' }); }
    finally { this.busy = false; }
  },
  beforeUnmount() { this.disposed = true; clearInterval(this.timer); },
  methods: {
    format(value) { return new Amount(value, 8).toString(); },
    edited() { this.revision++; this.quote = null; this.pin = false; this.error = ''; },
    async review() {
      if (this.busy || !this.amount || this.amount.value <= 0n) return;
      const revision = this.revision; this.busy = true; this.error = '';
      try { const quote = await this.$wallet.quoteBitcoin({ to: this.address.trim(), amountAtomic: String(this.amount.value), ...(this.exchange ? { exchangeOrderId: this.exchange.id } : {}) }); if (!this.disposed && revision === this.revision) this.quote = quote; }
      catch (error) { if (!this.disposed) this.error = paymentError(error, this.$t, { symbol: 'BTC' }); }
      finally { if (!this.disposed) this.busy = false; }
    },
    confirm() { if (this.expired || this.busy) return; this.approved = JSON.stringify(this.quote); this.pin = true; },
    async send(_, pin, pinActive) {
      const approved = this.approved;
      const current = () => !this.disposed && this.pin && pinActive() && this.approved === approved && JSON.stringify(this.quote) === approved;
      this.busy = true; this.error = '';
      try { const result = await this.$account.authorizeBitcoin(JSON.parse(approved), pin, current); if (!this.disposed) { this.result = result; this.pin = false; } }
      catch (error) { if (error.status === 401 || error.status === 429) throw error; if (!this.disposed) { this.error = paymentError(error, this.$t, { symbol: 'BTC' }); this.pin = false; } }
      finally { if (!this.disposed) this.busy = false; }
    },
  },
};
</script>
<template>
  <MainLayout class="&" :title="$t('Send {symbol}', { symbol: 'BTC' })" allowReadFailure>
    <h1>{{ $t(result ? 'Check payment status' : pin ? 'Enter your PIN' : 'Send {symbol}', { symbol: 'BTC' }) }}</h1>
    <MwNetworkSelector v-if="!quote && !result && !$route.query.exchange && !$route.query.claim" symbol="BTC" network="bitcoin" destination="crypto.send" />
    <p>{{ $t('Network') }} · Bitcoin</p>
    <p v-if="error" role="alert" class="&__error">{{ error }}</p>
    <section v-if="result" role="status"><p>{{ $t('We are checking this payment. Do not send it again.') }}</p><a :href="`https://mempool.space/tx/${result.hash}`" target="_blank" rel="noopener noreferrer">{{ result.hash }}</a><MwButton type="primary" @click="$router.push({ name: 'crypto.history', params: { cryptoId: $wallet.crypto._id } })">{{ $t('History') }}</MwButton></section>
    <template v-else>
      <form v-if="!quote" @submit.prevent="review"><fieldset :disabled="busy || !!$route.query.exchange"><MwFormInput v-model="address" :label="$t('Wallet address')" @update:modelValue="edited" /><MwFormAmountInput v-model="amount" :label="$t('Amount')" symbol="BTC" :decimals="8" @update:modelValue="edited" /></fieldset><MwButton type="primary" :isLoading="busy" :disabled="!amount || !address" @click="review">{{ $t('Review payment') }}</MwButton></form>
      <section v-else><dl><div><dt>{{ $t('Recipient receives') }}</dt><dd>{{ format(quote.amountAtomic) }} BTC</dd></div><div><dt>{{ $t('To') }}</dt><dd dir="ltr">{{ quote.to }}</dd></div><div><dt>{{ $t('Network cost') }}</dt><dd>{{ format(quote.maxFeeAtomic) }} BTC</dd></div><div><dt>{{ $t('Maximum total debit') }}</dt><dd>{{ total }} BTC</dd></div></dl><p v-if="expired" role="alert">{{ $t('This quote expired. Review the payment again.') }}</p><template v-if="!pin"><MwButton type="primary" :disabled="expired || busy" @click="confirm">{{ $t('Confirm and send') }}</MwButton><MwButton type="secondary" :disabled="busy" @click="edited">{{ $t('Review again') }}</MwButton></template><template v-else><p>{{ $t('Every signature and payment requires your approval and PIN.') }}</p><MwPin v-if="!expired || busy" mode="payment" :onSuccess="send" /><MwButton v-if="!busy" type="secondary" @click="pin = false">{{ $t('Back') }}</MwButton></template></section>
    </template>
  </MainLayout>
</template>
<style lang="scss">
.#{ $filename } {
  h1 { font-size: var(--text-title); font-weight: 600; }
  form, fieldset, section { display: flex; min-width: 0; flex-direction: column; gap: var(--space-lg); }
  fieldset { margin: 0; padding: 0; border: 0; }
  dl { margin: 0; } dl > div { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-sm); padding-block: var(--space-md); border-bottom: 1px solid var(--color-rule); }
  dt, p { color: var(--color-muted); font-size: var(--text-label); } dd { margin: 0; max-width: 100%; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
  a { overflow-wrap: anywhere; } &__error { color: var(--color-error); }
}
</style>
