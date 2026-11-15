<script>
import { rampError } from '../../lib/account/ramp-errors.js';
import { isPendingRampOrder, rampAssetSupported, rampNetwork } from '../../lib/account/ramp-presentation.js';
import MwButton from '../../components/MwButton.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormSelect from '../../components/MwForm/MwFormSelect.vue';
import MwLoader from '../../components/MwLoader.vue';
import CryptoRampOrderRow from './CryptoRampOrderRow.vue';
import CryptoRampOrderDetails from './CryptoRampOrderDetails.vue';
import eventBus from '../../lib/eventBus.js';

export default {
  components: { MwButton, MwFormAmountInput, MwFormSelect, MwLoader, CryptoRampOrderRow, CryptoRampOrderDetails },
  props: { kind: { type: String, required: true, validator: value => ['buy', 'sell'].includes(value) } },
  data() {
    return { amountValue: undefined, options: null, fiatCurrency: this.$currency, disposed: false, ordersRequest: null, isLoading: false, isLoadingOptions: false, isLoadingOrders: true, isRefreshing: false, error: '', optionsError: '', ordersError: '', feedback: '', request: null, currentOrder: null, selectedOrder: null, detailError: '', detailLoading: false, orders: [], poll: null, online: false, allowed: false };
  },
  computed: {
    providerAsset() { return this.options?.assets.find(item => item.symbol === this.$wallet.crypto.symbol); },
    currencies() { return (this.options?.fiatCurrencies || []).filter(item => item[this.kind]).map(item => ({ value: item.code, name: item.code })); },
    available() { return this.online && this.allowed && rampAssetSupported(this.options, this.providerAsset, this.kind) && this.currencies.some(item => item.value === this.fiatCurrency); },
    decimals() { return this.kind === 'buy' ? Math.min(6, this.options?.fiatCurrencies.find(item => item.code === this.fiatCurrency)?.decimals ?? 2) : Math.min(6, this.$wallet.crypto.decimals, this.providerAsset?.roundOff ?? this.$wallet.crypto.decimals); },
    symbol() { return this.kind === 'buy' ? this.fiatCurrency : this.$wallet.crypto.symbol; },
    network() { return rampNetwork(this.options?.environment); },
    canContinue() { return this.available && this.amountValue?.value > 0n && (!this.currentOrder || this.currentOrder.status === 'created'); },
    limited() { return this.options && (!rampAssetSupported(this.options, this.providerAsset, this.kind) || !this.currencies.length); },
  },
  mounted() {
    this.sync(); this.loadOptions(); this.loadOrders();
    eventBus.on('rampCheckoutUpdate', this.onCheckoutUpdate); eventBus.on('rampCheckoutError', this.onCheckoutError); eventBus.on('rampCheckoutClosed', this.onCheckoutClosed);
    this.$account.on('update', this.sync); window.addEventListener('online', this.resume); window.addEventListener('offline', this.sync); document.addEventListener('visibilitychange', this.resume); window.addEventListener('focus', this.resume);
    this.poll = setInterval(this.pollOrders, 20000);
  },
  beforeUnmount() {
    this.disposed = true; clearInterval(this.poll);
    eventBus.off('rampCheckoutUpdate', this.onCheckoutUpdate); eventBus.off('rampCheckoutError', this.onCheckoutError); eventBus.off('rampCheckoutClosed', this.onCheckoutClosed);
    this.$account.off('update', this.sync); window.removeEventListener('online', this.resume); window.removeEventListener('offline', this.sync); document.removeEventListener('visibilitychange', this.resume); window.removeEventListener('focus', this.resume);
  },
  methods: {
    sync() { this.online = navigator.onLine !== false && this.$account.connection === 'online'; this.allowed = this.$account.capabilities.ramp === true; },
    resume() { this.sync(); if (document.visibilityState === 'visible') { if (!this.options) this.loadOptions(); this.pollOrders(); } },
    async loadOptions() {
      if (this.disposed || this.isLoadingOptions) return;
      this.sync(); if (!this.allowed || !this.online) return;
      this.isLoadingOptions = true; this.optionsError = '';
      try {
        const options = await this.$wallet.rampOptions(); if (this.disposed) return;
        this.options = options;
        if (!this.currencies.some(item => item.value === this.fiatCurrency)) { this.fiatCurrency = this.currencies.find(item => item.value === 'USD')?.value || this.currencies[0]?.value || ''; this.reset(); }
        if (this.amountValue && this.amountValue.decimals !== this.decimals) this.reset();
      } catch (error) { if (!this.disposed) { this.options = null; this.optionsError = rampError(error, this.$t); } }
      finally { if (!this.disposed) this.isLoadingOptions = false; }
    },
    isPending: isPendingRampOrder,
    startAgain() { this.currentOrder = null; this.request = null; this.error = ''; this.feedback = ''; },
    reset() { this.amountValue = undefined; this.error = ''; this.request = null; this.currentOrder = null; },
    edited() { this.error = ''; this.request = null; this.currentOrder = null; },
    onCheckoutUpdate(order) { if (order?.cryptoCurrency === this.$wallet?.crypto.symbol) this.refreshPending(order.id); },
    onCheckoutError(order) { if (order?.cryptoCurrency === this.$wallet?.crypto.symbol) this.feedback = this.$t('Closing this screen does not cancel or confirm this order.'); },
    onCheckoutClosed(order) { if (order?.cryptoCurrency === this.$wallet?.crypto.symbol) { this.feedback = this.$t('Closing this screen does not cancel or confirm this order.'); this.$nextTick(() => this.$refs.feedback?.focus({ preventScroll: true })); } },
    async refresh() {
      if (this.isRefreshing || this.disposed) return;
      this.isRefreshing = true;
      try { await Promise.all([this.loadOptions(), this.loadOrders()]); }
      finally { if (!this.disposed) this.isRefreshing = false; }
    },
    loadOrders() {
      if (this.disposed) return Promise.resolve(); if (this.ordersRequest) return this.ordersRequest;
      const wallet = this.$wallet; this.ordersError = '';
      this.ordersRequest = Promise.resolve().then(() => wallet.rampOrders()).then(data => {
        if (this.disposed) return;
        this.orders = data.orders.filter(order => order.cryptoCurrency === wallet.crypto.symbol);
        if (this.currentOrder) this.currentOrder = this.orders.find(order => order.id === this.currentOrder.id) || this.currentOrder;
        if (this.selectedOrder) this.selectedOrder = this.orders.find(order => order.id === this.selectedOrder.id) || this.selectedOrder;
      }).catch(error => { if (!this.disposed) this.ordersError = rampError(error, this.$t); }).finally(() => { this.ordersRequest = null; if (!this.disposed) this.isLoadingOrders = false; });
      return this.ordersRequest;
    },
    async pollOrders() { this.sync(); if (!this.disposed && this.online && document.visibilityState === 'visible' && !this.isLoading && !this.ordersRequest && this.orders.some(isPendingRampOrder)) await this.loadOrders(); },
    async refreshPending(id) {
      if (this.disposed) return;
      try { const order = await this.$wallet.rampOrder(id); if (!this.disposed) this.upsertOrder(order); }
      catch (error) { if (!this.disposed) this.ordersError = rampError(error, this.$t); }
    },
    upsertOrder(order) {
      const index = this.orders.findIndex(item => item.id === order.id);
      if (index >= 0 && new Date(this.orders[index].updatedAt) > new Date(order.updatedAt)) return;
      if (index === -1) this.orders.unshift(order); else this.orders.splice(index, 1, order);
      if (this.currentOrder?.id === order.id) this.currentOrder = order;
      if (this.selectedOrder?.id === order.id) this.selectedOrder = order;
    },
    async openDetails(order) { this.selectedOrder = order; this.detailError = ''; await this.refreshDetail(); },
    async refreshDetail() {
      if (!this.selectedOrder || this.detailLoading) return;
      const id = this.selectedOrder.id; this.detailLoading = true; this.detailError = '';
      try { const order = await this.$wallet.rampOrder(id); if (!this.disposed) this.upsertOrder(order); }
      catch (error) { if (!this.disposed && this.selectedOrder?.id === id) this.detailError = rampError(error, this.$t); }
      finally { if (!this.disposed) this.detailLoading = false; }
    },
    async submit() {
      this.sync(); if (this.isLoading || !this.canContinue) return;
      this.isLoading = true; this.error = ''; this.feedback = '';
      try {
        const payload = { kind: this.kind, cryptoCurrency: this.$wallet.crypto.symbol, fiatCurrency: this.fiatCurrency, ...(this.kind === 'buy' ? { fiatAmount: this.amountValue.toString() } : { cryptoAmount: this.amountValue.toString() }) };
        const fingerprint = JSON.stringify(payload);
        if (this.request?.fingerprint !== fingerprint) this.request = { fingerprint, id: crypto.randomUUID() };
        const order = await this.$wallet.reviewRamp({ ...payload, idempotencyKey: this.request.id });
        if (this.disposed) return;
        this.currentOrder = order; this.upsertOrder(order); eventBus.emit('openRampCheckout', order);
      } catch (error) { if (!this.disposed) { this.error = rampError(error, this.$t); await this.loadOrders(); } }
      finally { if (!this.disposed) this.isLoading = false; }
    },
  },
};
</script>
<template>
  <div class="&">

    <h1 class="&__title">{{ $t(kind === 'buy' ? 'Buy {symbol}' : 'Sell {symbol}', { symbol: $wallet.crypto.symbol }) }}</h1>
    <p v-if="options?.environment === 'STAGING'" class="&__notice" role="status">{{ $t('Test mode. Do not use real money.') }}</p>
    <p v-if="!allowed || !online || limited" class="&__notice" role="status">{{ $t(!online ? 'The connection was interrupted. Check this order before trying again.' : !allowed ? 'The payment service is unavailable. Try again shortly.' : 'The selected asset or currency is unavailable.') }}</p>
    <p v-if="optionsError" class="&__error" role="alert">{{ optionsError }}</p>
    <MwLoader v-if="isLoadingOptions && !options" />
    <MwButton v-if="optionsError" type="secondary" :isLoading="isLoadingOptions" @click="loadOptions">{{ $t('Refresh') }}</MwButton>
    <form v-if="options && !limited" class="&__composition &__form" @submit.prevent="submit">
      <fieldset :disabled="isLoading || !available">
        <MwFormSelect v-model="fiatCurrency" :label="$t('Local currency')" :options="currencies" @update:modelValue="reset" />
        <MwFormAmountInput v-model="amountValue" :label="$t(kind === 'buy' ? 'You pay' : 'You sell')" :error="error" :decimals="decimals" :symbol="symbol" @update:modelValue="edited" />
      </fieldset>
      <aside class="&__summary" :aria-label="$t('Next step')">
        <h2>{{ $t('Next step') }}</h2>
        <dl><div><dt>{{ $t('Network') }}</dt><dd>{{ network }}</dd></div><div><dt>{{ $t('Amount') }}</dt><dd dir="ltr">{{ amountValue ? `${amountValue.toString()} ${symbol}` : '—' }}</dd></div><div><dt>{{ $t('Fee') }} · Transak</dt><dd>{{ $t('Shown before you confirm.') }}</dd></div></dl>
        <p>{{ $t('Review the exchange rate, fees and final amount before confirming.') }}</p>
      </aside>
      <MwButton v-if="currentOrder && currentOrder.status !== 'created'" class="&__continue" type="secondary" @click="isPending(currentOrder) ? openDetails(currentOrder) : startAgain()">{{ $t(isPending(currentOrder) ? 'Order details' : 'Review again') }}</MwButton>
      <MwButton v-else class="&__continue" type="primary" :isLoading="isLoading" :disabled="!canContinue" @click="submit">{{ $t('Continue') }}</MwButton>
    </form>
    <p v-if="feedback" ref="feedback" class="&__feedback" role="status" tabindex="-1">{{ feedback }}</p>
    <div v-if="currentOrder && currentOrder.status !== 'created'" class="&__notice" role="status">{{ $t('Your previous order remains available below.') }}</div>
    <section class="&__orders" :aria-label="$t('Recent orders')">
      <header class="&__orders-header"><h2>{{ $t('Recent orders') }}</h2><MwButton type="secondary" small :isLoading="isRefreshing" :disabled="isLoadingOrders || isLoading || !online" @click="refresh">{{ $t('Refresh') }}</MwButton></header>
      <p v-if="ordersError" class="&__error" role="alert">{{ ordersError }}</p><MwLoader v-if="isLoadingOrders" /><p v-else-if="!orders.length && !ordersError" class="&__empty">{{ $t('No orders yet.') }}</p>
      <CryptoRampOrderRow v-for="order in orders" :key="order.id" :order="order" interactive @select="openDetails(order)" />
    </section>
    <CryptoRampOrderDetails :order="selectedOrder" :isLoading="detailLoading" :error="detailError" @close="selectedOrder = null" @refresh="refreshDetail" />
  </div>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app · pre-emit critique: P5 H5 E4 S5 R5 V4 */
.#{ $filename } {
  min-width: 0;
  &__title { margin-block: var(--space-sm) var(--space-xl); font-size: var(--text-title); font-weight: 600; letter-spacing: -0.035em; overflow-wrap: anywhere; }
  &__composition { display: grid; grid-template-columns: minmax(0, 1fr); grid-template-areas: "fields" "summary" "action"; gap: var(--space-lg); }
  &__form { min-width: 0; }
  fieldset { grid-area: fields; display: flex; min-width: 0; flex-direction: column; gap: var(--space-lg); margin: 0; padding: 0; border: 0; }
  &__continue { grid-area: action; width: 100%; font-size: 0.875rem; white-space: nowrap; }
  &__summary { grid-area: summary; min-width: 0; padding-top: var(--space-lg); border-top: 1px solid var(--color-rule); }
  h2 { margin: 0; font-size: 1.0625rem; font-weight: 600; }
  dl { margin-block: var(--space-md); }
  dl > div { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-md); padding-block: var(--space-xs); }
  dt { color: var(--color-muted); font-size: var(--text-label); }
  dd { margin: 0; text-align: end; font-size: 0.9375rem; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
  &__summary p, &__notice, &__feedback, &__error, &__empty { font-size: var(--text-label); line-height: 1.6; }
  &__summary p, &__notice, &__empty { color: var(--color-muted); }
  &__notice { padding-block: var(--space-sm); }
  &__feedback { margin-block: var(--space-lg); padding-inline-start: var(--space-md); border-inline-start: 2px solid var(--color-accent); }
  &__error { color: var(--color-error); }
  &__orders { margin-top: var(--space-2xl); padding-top: var(--space-lg); border-top: 1px solid var(--color-rule); }
  &__orders-header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); margin-bottom: var(--space-md); }
  @media (min-width: 64rem) { &__composition { grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr); grid-template-areas: "fields summary" "action summary"; align-items: start; gap: var(--space-2xl); } &__summary { border-top: 0; border-inline-start: 1px solid var(--color-rule); padding-block: 0; padding-inline-start: var(--space-xl); } }
}
</style>
