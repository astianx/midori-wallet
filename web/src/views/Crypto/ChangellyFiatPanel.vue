<script>
import MwButton from '../../components/MwButton.vue';
import MwFormSelect from '../../components/MwForm/MwFormSelect.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import { rampError } from '../../lib/account/ramp-errors.js';
import { rampAmount, rampStatus, rampStatusMessage } from '../../lib/account/ramp-presentation.js';
import { safeOpen } from '../../lib/helpers.js';
export default {
  components: { MwButton, MwFormSelect, MwFormAmountInput },
  props: { kind: { type: String, required: true } },
  data() { return { options: null, country: '', state: '', fiatCurrency: this.$currency, amount: undefined, quotes: [], quoteId: '', order: null, orders: [], busy: false, error: '', disposed: false, revision: 0, idempotencyKey: '', now: Date.now(), timer: null }; },
  computed: {
    countries() { return (this.options?.countries || []).map(item => ({ value: item.code, name: item.name })); },
    states() { return (this.options?.countries.find(item => item.code === this.country)?.states || []).map(item => ({ value: item.code, name: item.name })); },
    currencies() { return (this.options?.currencies || []).map(item => ({ value: item.code, name: item.code })); },
    decimals() { return this.kind === 'buy' ? 2 : this.$wallet.crypto.decimals; },
    quote() { return this.quotes.find(item => item.id === this.quoteId); },
    expired() { return !this.quote || Date.parse(this.quote.expiresAt) <= this.now; },
    offerOptions() { return this.quotes.map(item => ({ value: item.id, name: `${item.providerCode} · ${item.paymentMethod} · ${this.money(item.amountTo, this.kind === 'buy' ? this.$wallet.crypto.symbol : this.fiatCurrency)}` })); },
  },
  mounted() { this.load(); this.timer = setInterval(() => { this.now = Date.now(); },1000); },
  beforeUnmount() { this.disposed = true; this.revision++; clearInterval(this.timer); },
  methods: {
    money: rampAmount,
    message(order) { return rampStatusMessage(order,this.$t); },
    status(order) { return rampStatus(order,this.$t); },
    edited() { this.revision++; this.quotes = []; this.quoteId = ''; this.idempotencyKey = ''; this.error = ''; },
    async load() { this.busy = true; this.error = ''; try { if (this.$account.capabilities.ramp) { const options = await this.$wallet.fiatOptions(this.kind); if (this.disposed) return; this.options = options; if (!this.currencies.some(item=>item.value === this.fiatCurrency)) this.fiatCurrency = this.currencies[0]?.value || ''; } await this.loadOrders(); } catch (error) { if (!this.disposed) this.error = rampError(error,this.$t); } finally { if (!this.disposed) this.busy = false; } },
    async loadOrders() { const result = await this.$wallet.fiatOrders(); if (!this.disposed) this.orders = result.orders.filter(item=>item.cryptoCurrency === this.$wallet.crypto.symbol && item.network === this.$wallet.crypto.platform); },
    async review() {
      if (this.busy || !this.amount || this.amount.value <= 0n || !this.country || this.country === 'US' && !this.state) return;
      const revision = this.revision; this.busy = true; this.error = '';
      try { const result = await this.$wallet.fiatQuote({ kind:this.kind,fiatCurrency:this.fiatCurrency,country:this.country,...(this.country === 'US' ? {state:this.state} : {}),amount:this.amount.toString() }); if (!this.disposed && revision === this.revision) { this.quotes = result.quotes; this.quoteId = result.quotes[0]?.id || ''; this.idempotencyKey = ''; if (!result.quotes.length) this.error = this.$t('The selected asset or currency is unavailable.'); } }
      catch (error) { if (!this.disposed) this.error = rampError(error,this.$t); }
      finally { if (!this.disposed) this.busy = false; }
    },
    async create() { if (this.busy || this.expired) return; this.busy = true; this.error = ''; this.idempotencyKey ||= crypto.randomUUID(); try { const order = await this.$wallet.fiatCreate({ quoteId:this.quoteId,idempotencyKey:this.idempotencyKey }); if (!this.disposed) { this.order = order; await this.loadOrders(); } } catch (error) { if (!this.disposed) this.error = rampError(error,this.$t); } finally { if (!this.disposed) this.busy = false; } },
    async refresh(id) { if (this.busy) return; this.busy = true; this.error = ''; try { const order = await this.$wallet.fiatOrder(id); if (!this.disposed) { this.order = order; await this.loadOrders(); } } catch (error) { if (!this.disposed) this.error = rampError(error,this.$t); } finally { if (!this.disposed) this.busy = false; } },
    checkout() { if (this.order?.checkoutUrl) safeOpen(this.order.checkoutUrl); },
  },
};
</script>
<template>
  <div class="&">
    <h1>{{ $t(kind === 'buy' ? 'Buy {symbol}' : 'Sell {symbol}', { symbol:$wallet.crypto.symbol }) }}</h1><p>Changelly · {{ $t('Provider') }}</p>
    <p v-if="!$account.capabilities.ramp" role="status">{{ $t('The payment service is unavailable. Try again shortly.') }}</p>
    <p v-if="options?.environment === 'STAGING'" role="status">{{ $t('Test mode. Do not use real money.') }}</p>
    <p v-if="error" role="alert" class="&__error">{{ error }}</p>
    <section v-if="order"><h2>{{ status(order) }}</h2><p>{{ $t('Reference') }} · {{ order.id }}</p><p>{{ message(order) }}</p><p v-if="order.network === 'spark'">{{ $t('Changelly uses the Bitcoin network. Spark conversion costs are reviewed separately.') }}</p><MwButton v-if="order.checkoutUrl && ['awaiting_payment','created','processing'].includes(order.status)" type="primary" @click="checkout">{{ $t('Continue') }} · {{ order.providerCode }}</MwButton><MwButton type="secondary" :isLoading="busy" @click="refresh(order.id)">{{ $t('Check payment status') }}</MwButton></section>
    <form v-else-if="options?.countries.length && options?.currencies.length" @submit.prevent="review"><fieldset :disabled="busy"><MwFormSelect v-model="country" :label="$t('Country/region')" :options="countries" @update:modelValue="state = ''; edited()" /><MwFormSelect v-if="states.length" v-model="state" :label="$t('Region')" :options="states" @update:modelValue="edited" /><MwFormSelect v-model="fiatCurrency" :label="$t('Local currency')" :options="currencies" @update:modelValue="edited" /><MwFormAmountInput v-model="amount" :label="$t(kind === 'buy' ? 'You pay' : 'You sell')" :decimals="decimals" :symbol="kind === 'buy' ? fiatCurrency : $wallet.crypto.symbol" @update:modelValue="edited" /></fieldset><MwButton type="primary" :disabled="!country || !amount || country === 'US' && !state" :isLoading="busy" @click="review">{{ $t('Review conversion') }}</MwButton>
      <section v-if="quotes.length"><MwFormSelect v-model="quoteId" :disabled="busy" :label="$t('Provider')" :options="offerOptions" @update:modelValue="idempotencyKey = ''" /><dl v-if="quote"><div><dt>{{ $t('Estimated received') }}</dt><dd>{{ money(quote.amountTo,kind === 'buy' ? $wallet.crypto.symbol : fiatCurrency) }}</dd></div><div><dt>{{ $t('Provider fee (included)') }}</dt><dd>{{ money(quote.fee,fiatCurrency) }}</dd></div><div><dt>{{ $t(kind === 'buy' ? 'Receive with Midori Wallet' : 'Your wallet address') }}</dt><dd dir="ltr">{{ quote.walletAddress }}</dd></div><div><dt>{{ $t('Network') }}</dt><dd>{{ quote.network === 'base' ? 'Base' : quote.network === 'spark' ? 'Bitcoin → Spark' : 'Bitcoin' }}</dd></div></dl><p v-if="quote?.network === 'spark'">{{ $t('Changelly uses the Bitcoin network. Spark conversion costs are reviewed separately.') }}</p><p>{{ $t('Changelly may request identity verification. The rate can change until conversion completes.') }}</p><p v-if="expired">{{ $t('This quote expired. Review the conversion again.') }}</p><MwButton type="primary" :isLoading="busy" :disabled="expired" @click="create">{{ $t('Continue') }}</MwButton></section>
    </form>
    <p v-else-if="options">{{ $t('The selected asset or currency is unavailable.') }}</p>
    <section><h2>{{ $t('Recent orders') }}</h2><p v-if="!orders.length">{{ $t('No orders yet.') }}</p><MwButton v-for="item in orders" :key="item.id" type="secondary" :disabled="busy" @click="refresh(item.id)">{{ status(item) }} · {{ item.id }}</MwButton><MwButton type="secondary" :isLoading="busy" @click="load">{{ $t('Refresh') }}</MwButton></section>
  </div>
</template>
<style lang="scss">
.#{ $filename } { min-width: 0; display: flex; flex-direction: column; gap: var(--space-lg); h1 { font-size: var(--text-title); font-weight: 600; } h2 { font-size: 1.125rem; font-weight: 600; } p { color: var(--color-muted); font-size: var(--text-label); line-height: 1.6; overflow-wrap: anywhere; } &__error { color: var(--color-error); } form, fieldset, section { display: flex; min-width: 0; flex-direction: column; gap: var(--space-md); } fieldset { margin: 0; padding: 0; border: 0; } dl { margin: 0; } dl > div { display: flex; justify-content: space-between; flex-wrap: wrap; gap: var(--space-sm); padding-block: var(--space-sm); border-bottom: 1px solid var(--color-rule); } dt { color: var(--color-muted); font-size: var(--text-label); } dd { margin: 0; max-width: 100%; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; } }
</style>
