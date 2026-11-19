<script>
import MainLayout from '../../../layouts/MainLayout.vue';
import MwButton from '../../../components/MwButton.vue';
import MwLoader from '../../../components/MwLoader.vue';
import MwFormAmountInput from '../../../components/MwForm/MwFormAmountInput.vue';
import MwFormSelect from '../../../components/MwForm/MwFormSelect.vue';
import { exchangeError } from '../../../lib/account/exchange-errors.js';
import { Amount } from '../../../lib/account/domain.js';

export default {
  components: { MainLayout, MwButton, MwLoader, MwFormAmountInput, MwFormSelect },
  data() { return { options: null, providerId: 'changelly', target: '', amount: undefined, quote: null, order: null, orders: [], loading: false, busy: false, error: '', ordersError: '', disposed: false, revision: 0, requestId: null, now: Date.now(), timer: null, online: navigator.onLine !== false }; },
  computed: {
    provider() { return this.options?.providers.find(item => item.id === this.providerId); },
    source() { return this.provider?.sources.find(item => this.matches(item, this.$wallet)); },
    address() { return this.destination?.address || ''; },
    sourceNetwork() { return this.$wallet.crypto.platform === 'bitcoin' ? 'Bitcoin' : this.$wallet.crypto.platform === 'spark' ? 'Spark / Lightning' : 'Base'; },
    sendingEnabled() { return ['bitcoin','spark'].includes(this.$wallet.crypto.platform) || this.$account.capabilities.payments === true; },
    destination() { return this.provider?.destinations.find(item => item.ticker === this.target); },
    targets() { return (this.provider?.destinations || []).filter(item => (item.providerTicker || item.ticker) !== (this.source?.providerTicker || this.source?.ticker)).map(item => ({ value: item.ticker, name: `${item.name} · ${item.network}` })); },
    providers() { return (this.options?.providers || []).map(item => ({ value: item.id, name: item.name })); },
    canQuote() { return this.online && !!this.source && !!this.destination && this.amount?.value > 0n && !!this.address; },
    expired() { return !this.quote || this.now >= Date.parse(this.quote.expiresAt); },
    canCreate() { return this.canQuote && !this.expired && this.sendingEnabled; },
    fundingWallet() { return this.order && this.$account.wallets().find(wallet => this.matches(this.order.quote.from, wallet)); },
    canFund() { return this.order?.status === 'waiting' && !!this.order.depositAddress && !this.order.paymentIntentId && !this.order.depositReference && !!this.fundingWallet && (['bitcoin','spark'].includes(this.fundingWallet.crypto.platform) || this.$account.capabilities.payments === true); },
  },
  mounted() { this.load(); this.timer = setInterval(() => { this.now = Date.now(); }, 1000); window.addEventListener('online', this.connectivity); window.addEventListener('offline', this.connectivity); },
  beforeUnmount() { this.disposed = true; clearInterval(this.timer); window.removeEventListener('online', this.connectivity); window.removeEventListener('offline', this.connectivity); },
  methods: {
    matches(asset, wallet) { return wallet.crypto.platform === 'spark' ? asset.walletNetwork === 'spark' : wallet.crypto.platform === 'bitcoin' ? !asset.walletNetwork && asset.ticker === 'btc' && /^bitcoin$/i.test(asset.network) : /^base$/i.test(asset.network) && asset.contract === wallet.crypto.address?.toLowerCase(); },
    connectivity() { this.online = navigator.onLine !== false; },
    format(value) { try { return Amount.fromString(value, 36).toString(); } catch { return '—'; } },
    edited() { this.revision++; this.quote = null; this.requestId = null; this.error = ''; },
    async load() {
      if (this.loading) return; this.loading = true; this.error = '';
      try {
        const options = await this.$wallet.exchangeOptions();
        if (this.disposed) return; this.options = options;
        if (!this.targets.some(item => item.value === this.target)) { this.target = this.targets.find(item => /usdt/i.test(item.value))?.value || this.targets[0]?.value || ''; this.edited(); }
      } catch (error) { if (!this.disposed) this.error = exchangeError(error, this.$t); }
      finally { if (!this.disposed) { this.loading = false; await this.loadOrders(); if (this.$route.query.order) await this.openOrder(this.$route.query.order); } }
    },
    async loadOrders() {
      this.ordersError = '';
      try { const result = await this.$wallet.exchangeOrders(); if (!this.disposed) this.orders = result.orders; }
      catch (error) { if (!this.disposed) this.ordersError = exchangeError(error, this.$t); }
    },
    async estimate() {
      if (this.busy || !this.canQuote) return;
      const revision = this.revision; this.busy = true; this.error = ''; this.quote = null;
      try { const quote = await this.$wallet.exchangeQuote({ from: this.source.ticker, to: this.target, amount: this.amount.toString() }); if (!this.disposed && revision === this.revision) this.quote = quote; }
      catch (error) { if (!this.disposed && revision === this.revision) this.error = exchangeError(error, this.$t); }
      finally { if (!this.disposed) this.busy = false; }
    },
    async create() {
      if (this.busy || !this.canCreate) return;
      this.busy = true; this.error = ''; this.requestId ||= crypto.randomUUID();
      try {
        const order = await this.$wallet.createExchange({ quoteId: this.quote.id, idempotencyKey: this.requestId, payoutAddress: this.address });
        if (this.disposed) return;
        const destination = this.$account.cryptoDB.all.find(asset => asset.symbol === order.quote.to.symbol && asset.platform === (order.quote.to.walletNetwork || (/^bitcoin$/i.test(order.quote.to.network) ? 'bitcoin' : 'base')));
        if (destination && !this.$account.hasWallet(destination._id)) await this.$account.addWallet(destination);
        this.order = order; this.quote = null; await this.$router.replace({ query: { order: order.id } }); await this.loadOrders();
      } catch (error) { if (!this.disposed) { this.error = exchangeError(error, this.$t); await this.loadOrders(); } }
      finally { if (!this.disposed) this.busy = false; }
    },
    async openOrder(id) {
      if (this.busy) return; this.busy = true; this.error = '';
      try { const order = await this.$wallet.exchangeOrder(id); if (!this.disposed) { this.order = order; if (this.$route.query.order !== id) await this.$router.replace({ query: { order: id } }); } }
      catch (error) { if (!this.disposed) this.error = exchangeError(error, this.$t); }
      finally { if (!this.disposed) this.busy = false; }
    },
    startAgain() { this.order = null; this.edited(); this.$router.replace({ query: {} }); },
    fund() { if (this.canFund) this.$router.push({ name: 'crypto.send', params: { cryptoId: this.fundingWallet.crypto._id }, query: { exchange: this.order.id } }); },
    trackPayment() { if (this.order?.paymentIntentId && this.fundingWallet) this.$router.push({ name: 'crypto.send', params: { cryptoId: this.fundingWallet.crypto._id }, query: { exchange: this.order.id } }); },
    status(order) { return this.$t({ creating: 'Processing', uncertain: 'Needs review', waiting: 'Awaiting payment', confirming: 'Processing', exchanging: 'Processing', sending: 'Processing', finished: 'Completed', failed: 'Failed', refunded: 'Refunded', hold: 'Needs review', expired: 'Expired', overdue: 'Expired' }[order.status] || 'Unavailable'); },
  },
};
</script>
<template>
  <MainLayout class="&" :title="$t('Swap {symbol}', { symbol: $wallet.crypto.symbol })" wide allowReadFailure>
    <h1>{{ $t('Swap {symbol}', { symbol: $wallet.crypto.symbol }) }}</h1>
    <p class="&__intro">{{ $t('Receive with Midori Wallet') }}</p>
    <p v-if="!online" class="&__error" role="status">{{ $t('The connection was interrupted. Check this order before trying again.') }}</p>
    <p v-if="error" class="&__error" role="alert">{{ error }}</p>
    <MwLoader v-if="loading" />
    <MwButton v-if="!options && !loading" type="secondary" @click="load">{{ $t('Refresh') }}</MwButton>
    <template v-if="order">
      <section class="&__review" :aria-label="$t('Order details')">
        <h2>{{ status(order) }} · Changelly</h2>
        <dl><div><dt>{{ $t('You send') }}</dt><dd>{{ format(order.quote.amountFrom) }} {{ order.quote.from.symbol }} · {{ order.quote.from.network }}</dd></div><div><dt>{{ $t('Destination blockchain') }}</dt><dd>{{ order.quote.to.name }} · {{ order.quote.to.network }}</dd></div><div><dt>{{ $t('Receive with Midori Wallet') }}</dt><dd>{{ order.payoutAddress }}</dd></div><div v-if="order.payoutExtraId"><dt>{{ $t('Destination tag / memo') }}</dt><dd>{{ order.payoutExtraId }}</dd></div><div><dt>{{ $t(order.status === 'finished' ? 'Receive' : 'Estimated received') }}</dt><dd>{{ format(order.status === 'finished' ? order.amountTo : order.quote.amountTo) }} {{ (order.quote.to.symbol || order.quote.to.ticker.toUpperCase()) }}</dd></div><div><dt>{{ $t('Reference') }}</dt><dd>{{ order.id }}</dd></div></dl>
        <p v-if="order.depositHash" role="status">{{ $t('We are checking this payment. Do not send it again.') }} <a :href="`https://mempool.space/tx/${order.depositHash}`" target="_blank" rel="noopener noreferrer">{{ order.depositHash }}</a></p><p v-if="['creating','uncertain'].includes(order.status)" role="status">{{ $t('The provider response is unconfirmed. No deposit was sent. Contact support before starting another conversion.') }}</p>
        <p v-else-if="order.status === 'hold'" role="status">{{ $t('Changelly may request identity verification. Contact Changelly support with your reference.') }} {{ order.providerId }}</p>
        <p v-else>{{ $t('The exchange rate is floating. Your wallet balance updates separately.') }}</p>
        <div class="&__actions"><MwButton v-if="canFund" type="primary" :disabled="!online || busy" @click="fund">{{ $t('Review deposit') }}</MwButton><MwButton v-else-if="order.paymentIntentId" type="primary" @click="trackPayment">{{ $t('Check payment status') }}</MwButton><MwButton type="secondary" :isLoading="busy" :disabled="!online" @click="openOrder(order.id)">{{ $t('Refresh') }}</MwButton><MwButton v-if="!['creating','uncertain'].includes(order.status)" type="primary-link" @click="startAgain">{{ $t('Back') }}</MwButton></div>
      </section>
    </template>
    <form v-else-if="options" class="&__form" @submit.prevent="estimate">
      <fieldset :disabled="busy || !online"><MwFormSelect v-if="providers.length > 1" v-model="providerId" :label="$t('Provider')" :options="providers" @update:modelValue="edited" /><p v-if="!source" role="status">{{ $t('The selected asset or currency is unavailable.') }}</p><template v-else><p>{{ $wallet.crypto.symbol }} · {{ sourceNetwork }}</p><MwFormSelect v-model="target" :label="$t('Destination blockchain')" :options="targets" @update:modelValue="edited" /><MwFormAmountInput v-model="amount" :label="$t('You send')" :symbol="$wallet.crypto.symbol" :decimals="$wallet.crypto.decimals" @update:modelValue="edited" /><section class="&__destination" :aria-label="$t('Your wallet address')"><strong>{{ $t('Receive with Midori Wallet') }}</strong><p>{{ destination?.symbol }} · {{ destination?.network }}</p><p dir="ltr">{{ address || '—' }}</p></section></template></fieldset>
      <p v-if="source?.notification" class="&__notice">{{ source.notification }}</p><p class="&__notice">{{ $t('Changelly may request identity verification. The rate can change until conversion completes.') }}</p>
      <MwButton v-if="source" type="primary" :disabled="!canQuote" :isLoading="busy" @click="estimate">{{ $t('Review conversion') }}</MwButton>
      <section v-if="quote" class="&__review" :aria-label="$t('Review conversion')"><h2>{{ $t('Review conversion') }}</h2><dl><div><dt>{{ $t('You send') }}</dt><dd>{{ format(quote.amountFrom) }} {{ $wallet.crypto.symbol }} · {{ sourceNetwork }}</dd></div><div><dt>{{ $t('Destination blockchain') }}</dt><dd>{{ quote.to.name }} · {{ quote.to.network }}</dd></div><div><dt>{{ $t('Receive with Midori Wallet') }}</dt><dd>{{ address }}</dd></div><div><dt>{{ $t('Estimated received') }}</dt><dd>{{ format(quote.amountTo) }} {{ (quote.to.symbol || quote.to.ticker.toUpperCase()) }}</dd></div><div><dt>{{ $t('Payout network cost (included)') }}</dt><dd>{{ format(quote.networkFee) }} {{ (quote.to.symbol || quote.to.ticker.toUpperCase()) }}</dd></div><div><dt>{{ $t('Provider fee (included)') }}</dt><dd>{{ quote.fee == null ? $t('Unavailable') : `${format(quote.fee)} ${(quote.to.symbol || quote.to.ticker.toUpperCase())}` }}</dd></div></dl><p>{{ $t('The deposit network cost is reviewed separately before you sign.') }}</p><p v-if="expired" role="status">{{ $t('This quote expired. Review the conversion again.') }}</p><p v-if="!sendingEnabled" role="status">{{ $t('Sending is temporarily unavailable. Your funds remain in your wallet.') }}</p><MwButton type="primary" :isLoading="busy" :disabled="!canCreate" @click="create">{{ $t('Continue') }}</MwButton></section>
    </form>
    <section class="&__orders" :aria-label="$t('Recent conversions')"><header><h2>{{ $t('Recent conversions') }}</h2><MwButton type="secondary" small :disabled="loading || busy || !online" @click="loadOrders">{{ $t('Refresh') }}</MwButton></header><p v-if="ordersError" class="&__error" role="alert">{{ ordersError }}</p><p v-else-if="!orders.length">{{ $t('No orders yet.') }}</p><button v-for="item in orders" :key="item.id" class="&__row" type="button" :disabled="busy" @click="openOrder(item.id)"><span>{{ item.quote.from.symbol }} → {{ item.quote.to.name }}<small>{{ item.quote.to.network }}</small></span><span>{{ status(item) }}</span></button></section>
  </MainLayout>
</template>
<style lang="scss">
.#{ $filename } {
  h1 { font-size: var(--text-title); font-weight: 600; letter-spacing: -0.035em; }
  h2 { margin: 0; font-size: 1.0625rem; font-weight: 600; }
  &__intro, &__notice, &__orders p, &__review p { color: var(--color-muted); font-size: var(--text-label); line-height: 1.6; }
  &__form, fieldset { display: flex; min-width: 0; flex-direction: column; gap: var(--space-lg); }
  fieldset { margin: 0; padding: 0; border: 0; }
  &__review { border-top: 1px solid var(--color-rule); padding-top: var(--space-lg); margin-block: var(--space-lg); }
  dl { margin-block: var(--space-md); }
  dl > div { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: var(--space-xs) var(--space-md); padding-block: var(--space-sm); }
  dt { color: var(--color-muted); font-size: var(--text-label); }
  &__destination { padding: var(--space-md); background: var(--color-surface-subtle); border-radius: var(--radius-control); overflow-wrap: anywhere; }
  dd { margin: 0; max-width: 100%; overflow-wrap: anywhere; font-size: 0.9375rem; font-variant-numeric: tabular-nums; }
  &__error { color: var(--color-error); font-size: var(--text-label); line-height: 1.6; }
  &__actions { display: flex; flex-wrap: wrap; gap: var(--space-md); }
  &__orders { margin-top: var(--space-2xl); border-top: 1px solid var(--color-rule); padding-top: var(--space-lg); }
  &__orders header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); }
  &__row { display: flex; width: 100%; min-height: 4.5rem; align-items: center; justify-content: space-between; gap: var(--space-md); border-bottom: 1px solid var(--color-rule); padding: var(--space-sm); text-align: start; font-size: var(--text-label); }
  &__row small { display: block; margin-top: var(--space-xs); color: var(--color-muted); }
  &__row:hover { background: var(--color-surface-subtle); }
}
</style>
