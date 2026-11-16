<script>
import { markRaw } from 'vue';
import { activityReader } from '../lib/account/activity-reader.js';
import { activityTitle, activityStatus, activityGroup, activityQuery, filterActivity } from '../lib/account/activity-presentation.js';
import MwAppIcon from './MwAppIcon.vue';
import MwButton from './MwButton.vue';
import MwActivityReceipt from './MwActivityReceipt.vue';
export default {
  components: { MwAppIcon, MwButton, MwActivityReceipt },
  emits: ['summary'],
  props: { compact: Boolean, symbol: { type: String, default: '' }, filters: Boolean },
  data() { const reader = activityReader(this.$account); return { reader: markRaw(reader), feed: reader.state, selected: null, detailLoading: false, detailError: '', selectionGeneration: 0, disposed: false, timer: null, openedHere: false, returnFocus: null }; },
  computed: {
    loading() { return this.feed.loading; },
    loaded() { return this.feed.loaded; },
    allRecords() { return this.reader.records(); },
    chosenFilters() {
      const query = this.$route.query;
      return { symbol: this.symbol || (['USDC', 'USDT'].includes(query.asset) ? query.asset : ''), kind: ['send', 'receive', 'buy', 'sell'].includes(query.type) ? query.type : '', state: ['pending', 'checking', 'completed', 'failed', 'refunded'].includes(query.status) ? query.status : '' };
    },
    records() { const result = this.compact ? this.allRecords : filterActivity(this.allRecords, this.chosenFilters); return this.compact ? result.slice(0, 5) : result; },
    pending() { return this.allRecords.find(item => ['pending', 'checking'].includes(activityGroup(item))); },
    summary() { return { loading: this.loading || !this.loaded, partial: this.partial, pending: !!this.pending }; },
    hasMore() { return this.feed.sources.some(source => source.hasMore); },
    filteredEmpty() { return Object.values(this.chosenFilters).some(Boolean); },
    partial() { return this.feed.ordersError || this.feed.sources.some(source => source.error); },
    indexing() { return this.feed.sources.some(source => source.indexing && source.indexing.status !== 'caught_up'); },
    selectionKey() { const q = this.$route.query; return this.compact ? '' : JSON.stringify([q.source, q.wallet, q.entry, q.payment]); },
    receiptOpen() { return !this.compact && !!(this.$route.query.entry || this.$route.query.payment); },
  },
  watch: {
    summary: { immediate: true, handler(value) { this.$emit('summary', value); } },
    selectionKey() { this.syncSelection(); },
    receiptOpen(value) { if (!value) { this.selected = null; this.detailError = ''; this.$nextTick(() => { if (this.returnFocus?.isConnected) this.returnFocus.focus({ preventScroll: true }); }); } },
  },
  mounted() { this.load(); this.timer = setInterval(() => { if (!document.hidden && !this.$account.isLocked && !this.receiptOpen) this.load(); }, 15000); },
  beforeUnmount() { this.disposed = true; this.selectionGeneration++; clearInterval(this.timer); },
  methods: {
    title(item) { return activityTitle(item, this.$t); },
    status(item) { return activityStatus(item, this.$t); },
    group: activityGroup,
    date(value, full = false) { const date = new Date(value); return Number.isFinite(date.valueOf()) ? this.$d(date, full ? 'short' : date.getFullYear() === new Date().getFullYear() ? 'shortCurrentYear' : 'short') : '—'; },
    day(value) { return new Date(value).toLocaleDateString(this.$i18n.locale, { year: 'numeric', month: 'long', day: 'numeric' }); },
    changeFilter(key, value) { this.$router.replace({ query: { ...this.$route.query, [key]: value || undefined } }); },
    clearFilters() { this.$router.replace({ query: { ...this.$route.query, asset: undefined, type: undefined, status: undefined } }); },
    openPending() { if (this.pending) this.open(this.pending); },
    open(item) {
      if (this.compact) return this.$router.push({ name: 'activity', query: activityQuery(item) });
      this.returnFocus = document.activeElement; this.openedHere = true;
      this.$router.push({ query: { ...this.$route.query, ...activityQuery(item), wallet: item.walletId || undefined, payment: undefined } });
    },
    close() {
      if (this.openedHere) { this.openedHere = false; this.$router.back(); }
      else this.$router.replace({ query: { ...this.$route.query, source: undefined, wallet: undefined, entry: undefined, payment: undefined } });
    },
    async syncSelection() {
      const generation = ++this.selectionGeneration;
      this.detailError = '';
      if (!this.receiptOpen) { this.detailLoading = false; return; }
      const q = this.$route.query, source = q.payment ? 'payment' : q.source, wallet = q.payment ? this.$wallet?.crypto._id : q.wallet, reference = q.payment ? `p${q.payment}` : q.entry;
      const cached = this.allRecords.find(item => item.source === source && item.reference === reference && (source !== 'payment' || item.walletId === wallet));
      this.selected = cached || null; this.detailLoading = true;
      try {
        const record = await this.reader.detail(source, wallet, reference);
        if (!this.disposed && generation === this.selectionGeneration) this.selected = record;
      } catch {
        if (!this.disposed && generation === this.selectionGeneration) this.detailError = this.$t(cached ? 'Could not update this receipt. Showing the last available details.' : 'Could not load this receipt. Check the link and try again.');
      } finally { if (!this.disposed && generation === this.selectionGeneration) this.detailLoading = false; }
    },
    async load(refresh = true) {
      await this.reader.load(refresh);
      if (this.receiptOpen && !this.disposed) await this.syncSelection();
    },
  },
};
</script>
<template>
  <div class="&" :class="{ '&--full': !compact }" :aria-busy="loading">
    <div v-if="filters" class="&__filters">
      <label>{{ $t('Stablecoins') }}<select :value="chosenFilters.symbol" @change="changeFilter('asset', $event.target.value)"><option value="">{{ $t('All') }}</option><option>USDC</option><option>USDT</option></select></label>
      <label>{{ $t('Type') }}<select :value="chosenFilters.kind" @change="changeFilter('type', $event.target.value)"><option value="">{{ $t('All') }}</option><option value="send">{{ $t('Sent') }}</option><option value="receive">{{ $t('Received') }}</option><option value="buy">{{ $t('Buy') }}</option><option value="sell">{{ $t('Sell') }}</option></select></label>
      <label>{{ $t('Status') }}<select :value="chosenFilters.state" @change="changeFilter('status', $event.target.value)"><option value="">{{ $t('All') }}</option><option value="pending">{{ $t('Pending') }}</option><option value="checking">{{ $t('Checking') }}</option><option value="completed">{{ $t('Completed') }}</option><option value="failed">{{ $t('Not completed') }}</option><option value="refunded">{{ $t('Refunded') }}</option></select></label>
    </div>
    <div class="&__toolbar"><span role="status">{{ loading ? $t('Loading') : feed.updatedAt ? $t('Last updated {time}', { time: date(feed.updatedAt) }) : '' }}</span><MwButton type="primary-link" small :isLoading="loading" @click="load()">{{ $t('Refresh') }}</MwButton></div>
    <div v-if="loading && !records.length" class="&__loading" role="status" :aria-label="$t('Loading')"><div /><div /><div /></div>
    <div v-if="partial" class="&__notice" role="status">{{ $t('Some activity could not be loaded.') }}</div>
    <div v-if="!loading && !records.length && !partial" class="&__empty"><span class="&__empty-icon"><MwAppIcon name="activity" /></span><h3>{{ $t(filteredEmpty ? 'No matching activity' : 'No activity yet') }}</h3><p>{{ $t('Your transfers and orders appear here.') }}</p><MwButton v-if="filters && filteredEmpty" type="primary-link" @click="clearFilters">{{ $t('Reset filter') }}</MwButton><RouterLink v-else :to="{ name: 'add-money' }">{{ $t('Add money') }}<MwAppIcon name="arrow" /></RouterLink></div>
    <div v-if="records.length && !compact" class="&__heading" aria-hidden="true"><span>{{ $t('Type') }}</span><span>{{ $t('Date') }}</span><span>{{ $t('Status') }}</span><span>{{ $t('Amount') }}</span></div>
    <ul v-if="records.length" class="&__list"><li v-for="(item, index) in records" :key="item.id"><h3 v-if="!compact && (!index || day(item.timestamp) !== day(records[index - 1].timestamp))" class="&__day">{{ day(item.timestamp) }}</h3><button type="button" class="&__row" :data-reference="item.reference" @click="open(item)"><span class="&__icon"><MwAppIcon :name="item.kind === 'buy' ? 'plus' : item.kind === 'receive' ? 'receive' : 'send'" /></span><span class="&__description"><strong>{{ title(item) }}</strong><small :data-tone="group(item)">{{ status(item) }}<span v-if="item.environment === 'STAGING'"> · {{ $t('Test mode') }}</span></small><small v-if="!compact && item.recipientTag" class="&__counterparty">${{ item.recipientTag.replace(/^[$@]/, '') }}</small></span><span v-if="!compact" class="&__date">{{ date(item.timestamp) }}</span><span v-if="!compact" class="&__state">{{ status(item) }}<span v-if="item.environment === 'STAGING'"> · {{ $t('Test mode') }}</span></span><span class="&__values"><strong dir="ltr">{{ $isHiddenBalance ? '••••' : item.amount }}</strong><small>{{ date(item.timestamp) }}</small></span></button></li></ul>
    <div v-if="!compact" class="&__scope"><p>{{ $t('Filters apply to loaded activity. Load more to search older transfers.') }}</p><p v-if="$account.capabilities.ramp">{{ $t('Purchases and sales show the latest 50 orders.') }}</p><p v-if="indexing" role="status">{{ $t('History is still updating. More activity may appear.') }}</p></div>
    <MwButton v-if="!compact && hasMore" type="secondary" :isLoading="loading" @click="load(false)">{{ $t('Load more') }}</MwButton>
    <MwActivityReceipt :show="receiptOpen" :record="selected" :loading="detailLoading" :error="detailError" @close="close" @refresh="syncSelection" />
  </div>
</template>
<style lang="scss">
.#{ $filename } {
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P4 H5 E5 S5 R5 V4 */
  min-width: 0; padding: var(--space-xs); border: 1px solid var(--color-rule); border-radius: var(--radius-panel); background: var(--color-surface); color: var(--color-ink);
  &__toolbar { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); padding-inline: var(--space-sm); }
  &__toolbar > span { color: var(--color-muted); font-size: 0.75rem; overflow-wrap: anywhere; }
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
  &--full { border: 0; padding: 0; }
  &__scope { padding: var(--space-sm); color: var(--color-muted); font-size: 0.75rem; line-height: 1.6; }
  &__scope p { margin-block: var(--space-xs); }
  &__filters { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-sm); padding: var(--space-sm); }
  &__filters label { display: grid; gap: var(--space-xs); font-size: var(--text-label); color: var(--color-muted); min-width: 0; }
  &__filters select { width: 100%; min-width: 0; min-height: 2.75rem; border: 1px solid var(--color-rule); border-radius: var(--radius-control); background: var(--color-surface-subtle); padding: var(--space-xs); color: var(--color-ink); font: inherit; }
  &__date, &__state { display: none; }
  &__row:active { background: var(--color-surface-tint); }
  &__day { margin: 0; padding: var(--space-md) var(--space-xs) var(--space-xs); color: var(--color-muted); font-size: var(--text-label); font-weight: 500; }
  &__heading { display: none; }
  &__description small[data-tone='completed'] { color: var(--color-accent); }
  &__description small[data-tone='failed'] { color: var(--color-error); }
  @media (min-width: 64rem) {
    &--full &__heading { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1fr); gap: var(--space-sm); padding: var(--space-md) var(--space-xs); font-size: var(--text-label); color: var(--color-muted); border-bottom: 1px solid var(--color-rule); }
    &--full &__heading span:last-child { text-align: end; }
    &--full &__row { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1fr); }
    &--full &__icon, &--full &__description small, &--full &__values small { display: none; }
    &--full &__date, &--full &__state { display: block; font-size: var(--text-label); color: var(--color-muted); overflow-wrap: anywhere; }
    &--full &__values { max-width: none; }
    &--full &__description &__counterparty { display: block; }
    &--full &__day { border-bottom: 1px solid var(--color-rule); }
  }
  @media (max-width: 47.99rem) { &__filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } &__filters label:last-child { grid-column: 1 / -1; } }
  @media (max-width: 23rem) { &__filters { grid-template-columns: minmax(0, 1fr); } &__icon { width: 2rem; height: 2rem; } }
}
@keyframes mw-activity-loading { 50% { opacity: 0.45; } }
@media (prefers-reduced-motion: reduce) { .mw-activity-list__loading > div { animation: none; } }
</style>
