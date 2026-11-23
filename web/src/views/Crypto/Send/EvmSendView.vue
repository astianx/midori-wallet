<script>
import MainLayout from '../../../layouts/MainLayout.vue';
import MwButton from '../../../components/MwButton.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwFormAmountInput from '../../../components/MwForm/MwFormAmountInput.vue';
import MwNetworkSelector from '../../../components/MwNetworkSelector.vue';
import MwPin from '../../../components/MwPin.vue';
import { Amount } from '../../../lib/account/domain.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
export default {
  components: { MainLayout, MwButton, MwFormInput, MwFormAmountInput, MwNetworkSelector, MwPin },
  data() { return { address: this.$route.query.address || '', amount: undefined, quote: null, approved: '', pin: false, busy: true, error: '', result: null, disposed: false, revision: 0, now: Date.now(), timer: null }; },
  computed: {
    network() { return this.$wallet.network; },
    expired() { return !this.quote || this.now >= Date.parse(this.quote.expiresAt); },
    tracking() { return this.result && !['confirmed','reverted'].includes(this.result.status); },
    fee() { return this.quote ? new Amount(this.quote.maxFeeAtomic, 18).toString() : '—'; },
  },
  async mounted() {
    this.timer = setInterval(() => { this.now = Date.now(); }, 500);
    try { this.result = await this.$wallet.pendingEvm(); }
    catch (error) { this.error = paymentError(error, this.$t); }
    finally { this.busy = false; }
  },
  beforeUnmount() { this.disposed = true; this.revision++; clearInterval(this.timer); },
  methods: {
    format(value, decimals = this.$wallet.crypto.decimals) { return new Amount(value, decimals).toString(); },
    edited() { this.revision++; this.quote = null; this.pin = false; this.error = ''; },
    async review() {
      if (this.busy || !this.amount || this.amount.value <= 0n) return;
      const revision = this.revision; this.busy = true; this.error = '';
      try { const quote = await this.$wallet.quoteEvm({ to: this.address.trim(), amountAtomic: String(this.amount.value) }); if (!this.disposed && revision === this.revision) this.quote = quote; }
      catch (error) { if (!this.disposed) this.error = error.code === 'INSUFFICIENT_GAS_FUNDS' ? this.$t('Add {symbol} on {network} to cover the network cost.', { symbol: this.network.nativeSymbol, network: this.network.name }) : paymentError(error, this.$t, { symbol: this.$wallet.crypto.symbol }); }
      finally { if (!this.disposed) this.busy = false; }
    },
    confirm() { if (this.expired || this.busy) return; this.approved = JSON.stringify(this.quote); this.pin = true; },
    async send(_, pin, pinActive) {
      const approved = this.approved, revision = this.revision, asset = this.$wallet.crypto;
      const current = () => !this.disposed && revision === this.revision && this.pin && pinActive() && this.approved === approved && JSON.stringify(this.quote) === approved;
      this.busy = true; this.error = '';
      try { const result = await this.$account.authorizeEvm(JSON.parse(approved), pin, asset, current); if (!this.disposed) { this.result = result; this.pin = false; } }
      catch (error) { if (error.status === 401 || error.status === 429) throw error; if (!this.disposed) { this.error = paymentError(error, this.$t); this.pin = false; } }
      finally { if (!this.disposed) this.busy = false; }
    },
    async retry() {
      if (!this.result || this.busy) return;
      this.busy = true; this.error = '';
      try { const result = await this.$wallet.retryEvm(this.result.hash); if (!this.disposed) this.result = { ...this.result,...result }; }
      catch { if (!this.disposed) this.error = this.$t('The connection was interrupted. Check this payment before trying again.'); }
      finally { if (!this.disposed) this.busy = false; }
    },
    async check() {
      if (!this.result || this.busy) return;
      this.busy = true; this.error = '';
      try { const result = await this.$wallet.request('status', { hash: this.result.hash }); if (!this.disposed) this.result = { ...this.result, ...result }; }
      catch { if (!this.disposed) this.error = this.$t('The connection was interrupted. Check this payment before trying again.'); }
      finally { if (!this.disposed) this.busy = false; }
    },
  },
};
</script>
<template>
  <MainLayout class="&" :title="$t('Send {symbol}', { symbol: $wallet.crypto.symbol })" allowReadFailure>
    <h1>{{ $t(result ? 'Check payment status' : pin ? 'Enter your PIN' : 'Send {symbol}', { symbol: $wallet.crypto.symbol }) }}</h1>
    <MwNetworkSelector v-if="!quote && !result" :symbol="$wallet.crypto.symbol" :network="network.id" destination="crypto.send" />
    <p v-else>{{ $t('Network') }} · {{ network.name }}</p>
    <p v-if="error" role="alert" class="&__error">{{ error }}</p>
    <section v-if="result" role="status">
      <p>{{ $t(result.status === 'confirmed' ? 'Payment confirmed' : result.status === 'reverted' ? 'Payment failed' : 'We are checking this payment. Do not send it again.') }}</p>
      <p v-if="result.quote">{{ format(result.quote.amountAtomic, result.quote.decimals) }} {{ result.quote.symbol }}</p>
      <a :href="`${network.explorer}/tx/${result.hash}`" target="_blank" rel="noopener noreferrer">{{ $t('View in Block Explorer') }}</a>
      <MwButton v-if="tracking" type="primary" :isLoading="busy" @click="check">{{ $t('Check payment status') }}</MwButton>
      <MwButton v-if="result.status === 'ambiguous'" type="secondary" :isLoading="busy" @click="retry">{{ $t('Retry the same transaction') }}</MwButton>
      <MwButton v-if="!tracking" type="primary" @click="result = null; edited()">{{ $t('Back') }}</MwButton>
    </section>
    <template v-else>
      <form v-if="!quote" @submit.prevent="review"><fieldset :disabled="busy"><MwFormInput v-model="address" :label="$t('Wallet address')" placeholder="0x…" @update:modelValue="edited" /><MwFormAmountInput v-model="amount" :label="$t('Amount')" :symbol="$wallet.crypto.symbol" :decimals="$wallet.crypto.decimals" @update:modelValue="edited" /></fieldset><p>{{ $t('Network fees are paid in {symbol} on {network}.', { symbol: network.nativeSymbol, network: network.name }) }}</p><MwButton type="primary" :isLoading="busy" :disabled="!amount || !address" @click="review">{{ $t('Review payment') }}</MwButton></form>
      <section v-else><dl><div><dt>{{ $t('Recipient receives') }}</dt><dd>{{ format(quote.amountAtomic) }} {{ quote.symbol }}</dd></div><div><dt>{{ $t('To') }}</dt><dd dir="ltr">{{ quote.to }}</dd></div><div><dt>{{ $t('Network') }}</dt><dd>{{ network.name }}</dd></div><div><dt>{{ $t('Maximum network cost') }}</dt><dd>{{ fee }} {{ network.nativeSymbol }}</dd></div></dl><p>{{ $t('Network fees are paid in {symbol} on {network}.', { symbol: network.nativeSymbol, network: network.name }) }}</p><p v-if="expired" role="alert">{{ $t('This quote expired. Review the payment again.') }}</p><template v-if="!pin"><MwButton type="primary" :disabled="expired || busy" @click="confirm">{{ $t('Confirm and send') }}</MwButton><MwButton type="secondary" :disabled="busy" @click="edited">{{ $t('Review again') }}</MwButton></template><template v-else><p>{{ $t('Every signature and payment requires your approval and PIN.') }}</p><MwPin v-if="!expired || busy" mode="payment" :onSuccess="send" /><MwButton v-if="!busy" type="secondary" @click="pin = false">{{ $t('Back') }}</MwButton></template></section>
    </template>
  </MainLayout>
</template>
<style lang="scss">
.#{ $filename } {
  h1 { font-size: var(--text-title); font-weight: 600; }
  form, fieldset, section { display: flex; min-width: 0; flex-direction: column; gap: var(--space-lg); }
  fieldset { margin: 0; padding: 0; border: 0; }
  dl { margin: 0; } dl > div { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-sm); padding-block: var(--space-md); border-bottom: 1px solid var(--color-rule); }
  dt, p { color: var(--color-muted); font-size: var(--text-label); line-height: 1.5; } dd { margin: 0; max-width: 100%; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
  a { overflow-wrap: anywhere; color: var(--color-primary); } &__error { color: var(--color-danger); }
}
</style>
