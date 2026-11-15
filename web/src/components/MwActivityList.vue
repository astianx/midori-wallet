<script>
import MwAppIcon from './MwAppIcon.vue';
import MwButton from './MwButton.vue';
import MwModal from './MwModal.vue';
export default {
  components: { MwAppIcon, MwButton, MwModal },
  props: { compact: { type: Boolean, default: false }, symbol: { type: String, default: '' } },
  data() { return { sources: this.$account.wallets().map(wallet => ({ id: wallet.crypto._id, symbol: wallet.crypto.symbol, cursor: undefined, hasMore: true, paginated: false, records: [], error: false })), orders: [], loading: false, ordersError: false, selected: null, disposed: false, timer: null }; },
  computed: {
    records() {
      const payments = this.sources.flatMap(source => source.records);
      const records = [...payments, ...this.orders].filter(item => !this.symbol || item.symbol === this.symbol).sort((a, b) => b.timestamp - a.timestamp || a.id.localeCompare(b.id));
      return this.compact ? records.slice(0, 5) : records;
    },
    hasMore() { return this.sources.some(source => source.hasMore); },
    filteredEmpty() { return Boolean(this.symbol) && (this.orders.length > 0 || this.sources.some(source => source.records.length)); },
    partial() { return this.ordersError || this.sources.some(source => source.error); },
  },
  mounted() { this.load(true, true); this.timer = setInterval(() => { if (!document.hidden && !this.$account.isLocked) this.load(true); }, 15000); },
  beforeUnmount() { this.disposed = true; clearInterval(this.timer); },
  methods: {
    status(value) { return this.$t(({ ambiguous: 'We are checking this payment. Do not send it again.', confirmed: 'Confirmed', completed: 'Completed', reverted: 'Payment reverted', expired: 'Expired', failed: 'Failed', refunded: 'Refunded', awaiting_payment: 'Awaiting payment', processing: 'Processing', created: 'Started', reorganized: 'Reorganized', included: 'Included, awaiting confirmations' })[value] || 'Confirming payment'); },
    async load(reset = false, restart = false) {
      if (this.loading || this.disposed || this.$account.isLocked) return;
      this.loading = true;
      await Promise.all(this.sources.filter(source => reset || source.hasMore).map(async source => {
        try {
          const data = await this.$account.wallet(source.id).loadTransactions({ cursor: reset ? undefined : source.cursor });
          if (this.disposed) return;
          const incoming = data.transactions.map(tx => ({ ...tx, source: 'payment', id: `${source.id}:${tx.id}`, reference: tx.id, symbol: source.symbol, timestamp: new Date(tx.timestamp), label: this.$t(tx.incoming ? 'Received' : 'Sent'), amount: `${tx.incoming ? '+' : '−'}${tx.amount} ${source.symbol}`, state: tx.chainStatus }));
          const keepPage = reset && source.paginated && !restart;
          source.records = reset && !keepPage ? incoming : [...new Map([...source.records, ...incoming].map(item => [item.id, item])).values()];
          if (!keepPage) { source.cursor = data.cursor; source.hasMore = Boolean(data.hasMore); }
          source.paginated = keepPage || !reset; source.error = false;
        } catch { if (!this.disposed) source.error = true; }
      }));
      if (reset && this.$account.capabilities.ramp && this.sources[0]) {
        try {
          const data = await this.$account.wallet(this.sources[0].id).rampOrders();
          if (!this.disposed) { this.orders = data.orders.map(order => ({ ...order, source: 'ramp', id: `ramp:${order.id}`, reference: order.id, symbol: order.cryptoCurrency, timestamp: new Date(order.updatedAt), label: `Transak · ${this.$t(order.kind === 'buy' ? 'Buy {symbol}' : 'Sell {symbol}', { symbol: order.cryptoCurrency })}`, amount: order.kind === 'buy' ? `${order.fiatAmount ?? '—'} ${order.fiatCurrency}` : `${order.cryptoAmount ?? '—'} ${order.cryptoCurrency}`, state: order.status })); this.ordersError = false; }
        } catch { if (!this.disposed) this.ordersError = true; }
      }
      if (!this.disposed) this.loading = false;
    },
  },
};
</script>
<template>
  <div class="&" :aria-busy="loading">
    <div v-if="loading && !records.length" class="&__loading" role="status" :aria-label="$t('Loading')"><div /><div /><div /></div>
    <div v-if="partial" class="&__notice" role="status"><span>{{ $t('Some activity could not be loaded.') }}</span><MwButton type="primary-link" small :isLoading="loading" @click="load(true, true)">{{ $t('Refresh') }}</MwButton></div>
    <div v-if="!loading && !records.length && !partial" class="&__empty"><span class="&__empty-icon"><MwAppIcon name="activity" /></span><h3>{{ $t(filteredEmpty ? 'No matching activity' : 'No activity yet') }}</h3><p>{{ $t(filteredEmpty ? 'Choose another stablecoin or show all activity.' : 'Your transfers and orders appear here.') }}</p><RouterLink v-if="!filteredEmpty" :to="{ name: 'receive' }">{{ $t('Receive') }}<MwAppIcon name="arrow" /></RouterLink></div>
    <ul v-if="records.length" class="&__list"><li v-for="item in records" :key="item.id"><button type="button" class="&__row" @click="selected = item"><span class="&__icon"><MwAppIcon :name="item.source === 'ramp' ? 'plus' : item.incoming ? 'receive' : 'send'" /></span><span class="&__description"><strong>{{ item.label }}</strong><small>{{ status(item.state) }}<span v-if="item.environment === 'STAGING'"> · {{ $t('Test mode') }}</span></small></span><span class="&__values"><strong dir="ltr">{{ $isHiddenBalance ? '••••' : item.amount }}</strong><small>{{ $d(item.timestamp, 'shortCurrentYear') }}</small></span></button></li></ul>
    <MwButton v-if="!compact && hasMore" type="secondary" :isLoading="loading" @click="load()">{{ $t('Load more') }}</MwButton>
    <MwModal :show="!!selected" :title="selected?.label || $t('Activity')" @close="selected = null"><template v-if="selected"><div class="&__receipt-amount">{{ $isHiddenBalance ? '••••' : selected.amount }}</div><dl class="&__details"><dt>{{ $t('Status') }}</dt><dd>{{ status(selected.state) }}</dd><dt>{{ $t('Network') }}</dt><dd>Base <span v-if="selected.environment === 'STAGING'">· {{ $t('Test mode') }}</span></dd><dt>{{ $t('Date') }}</dt><dd>{{ $d(selected.timestamp, 'short') }}</dd><dt>{{ $t('Transaction ID') }}</dt><dd>{{ selected.transactionHash || selected.reference }}</dd><template v-if="selected.to || selected.walletAddress"><dt>{{ $t('Wallet address') }}</dt><dd>{{ selected.to || selected.walletAddress }}</dd></template><template v-if="selected.networkFee"><dt>{{ $t('Network cost') }}</dt><dd>{{ $isHiddenBalance ? '••••' : `${selected.networkFee} ${selected.symbol}` }}</dd></template></dl><MwButton v-if="selected.url" type="secondary" @click="$safeOpen(selected.url)">{{ $t('Explorer') }}<MwAppIcon name="arrow" /></MwButton></template></MwModal>
  </div>
</template>
<style lang="scss">
.#{ $filename } {
  min-width: 0; padding: var(--space-xs); border: 1px solid var(--color-rule); border-radius: var(--radius-panel); background: var(--color-surface); color: var(--color-ink);
  &__list { margin: 0; padding: 0; list-style: none; }
  &__row { display: flex; width: 100%; min-width: 0; align-items: center; gap: var(--space-sm); text-align: start; padding: var(--space-md) var(--space-xs); border-bottom: 1px solid var(--color-rule); }
  li:last-child &__row { border-bottom: 0; }
  &__row:hover { background: var(--color-surface-subtle); color: var(--color-ink); border-radius: var(--radius-control); }
  &__icon, &__empty-icon { display: grid; flex: none; place-items: center; width: 2.5rem; height: 2.5rem; border-radius: var(--radius-control); background: var(--color-surface-tint); color: var(--color-accent); }
  &__icon svg { width: 1.125rem; height: 1.125rem; }
  &__description { flex: 1; min-width: 0; }
  &__description strong, &__values strong { display: block; font-size: 0.8125rem; font-weight: 550; overflow-wrap: anywhere; }
  &__description small, &__values small { display: block; color: var(--color-muted); font-size: 0.75rem; margin-top: var(--space-2xs); }
  &__values { max-width: 48%; text-align: end; font-variant-numeric: tabular-nums; }
  &__empty { display: flex; align-items: center; flex-direction: column; padding: var(--space-xl) var(--space-md); text-align: center; }
  &__empty-icon { width: 3rem; height: 3rem; margin-bottom: var(--space-md); }
  &__empty-icon svg { width: 1.5rem; height: 1.5rem; }
  &__empty h3 { font-size: 0.9375rem; margin: 0; font-weight: 550; }
  &__empty p { color: var(--color-muted); margin-block: var(--space-xs) var(--space-md); font-size: 0.8125rem; max-width: 38ch; }
  &__empty a { display: flex; align-items: center; gap: var(--space-xs); font-size: 0.8125rem; font-weight: 550; min-height: 2.75rem; }
  &__empty a svg { width: 1rem; height: 1rem; }
  &__notice { display: flex; align-items: center; flex-wrap: wrap; justify-content: space-between; padding: var(--space-sm); gap: var(--space-sm); font-size: var(--text-label); color: var(--color-warning); }
  &__loading { padding: var(--space-md); }
  &__loading > div { height: 2.75rem; margin-block: var(--space-sm); border-radius: var(--radius-control); background: var(--color-surface-subtle); animation: mw-activity-loading 1.5s var(--ease-in-out) infinite; }
  &__receipt-amount { font-size: 1.75rem; font-weight: 600; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
  &__details { display: grid; gap: var(--space-xs); font-size: 0.875rem; }
  &__details dt { color: var(--color-muted); margin-top: var(--space-sm); }
  &__details dd { margin: 0; overflow-wrap: anywhere; }
}
@keyframes mw-activity-loading { 50% { opacity: 0.45; } }
</style>
