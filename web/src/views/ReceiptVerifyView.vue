<script>
import Logo from '../assets/svg/logo.svg';
import MwButton from '../components/MwButton.vue';
import { receiptContent, receiptDate } from '../lib/receipt-document.js';
import { activityStatus } from '../lib/account/activity-presentation.js';
export default {
  components: { Logo, MwButton },
  data() { return { result: null, loading: false, failed: false, controller: null, robots: null }; },
  computed: {
    content() { return this.result ? receiptContent(this.result.document, this.$t, this.$i18n.locale) : null; },
    currentStatus() { return activityStatus({ state: this.result?.currentStatus || 'ambiguous' }, this.$t); },
  },
  watch: { '$route.hash'() { this.verify(); } },
  mounted() { this.robots = document.createElement('meta'); this.robots.name = 'robots'; this.robots.content = 'noindex,nofollow'; document.head.append(this.robots); this.verify(); },
  beforeUnmount() { this.controller?.abort(); this.robots?.remove(); },
  methods: {
    date(value) { return receiptDate(value, this.$i18n.locale); },
    async verify() {
      this.controller?.abort(); const controller = new AbortController(); this.controller = controller;
      this.loading = true; this.failed = false; this.result = null;
      try {
        const token = this.$route.hash.slice(1); if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw Error('Invalid reference');
        const origin = import.meta.env.VITE_API_ORIGIN || location.origin;
        const response = await fetch(new URL('/api/v1/receipts/verify', origin), { method: 'POST', credentials: 'omit', referrerPolicy: 'no-referrer', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }), signal: controller.signal });
        if (!response.ok) throw Error('Unavailable');
        const result = await response.json(); if (!result.document || !result.document.date || !result.digest) throw Error('Invalid response');
        if (this.controller === controller) this.result = result;
      } catch (error) { if (error.name !== 'AbortError' && this.controller === controller) this.failed = true; }
      finally { if (this.controller === controller) this.loading = false; }
    },
  },
};
</script>
<template>
  <main class="&"><header><Logo aria-label="Midori Wallet" /><span>Midori Wallet</span></header><h1>{{ $t('Verify receipt') }}</h1><p v-if="loading" role="status">{{ $t('Loading') }}</p><p v-if="failed" role="alert">{{ $t('Receipt unavailable.') }}</p><section v-if="result" :data-receipt-id="result.document.id"><p class="&__verified">{{ $t('Verified from Midori records.') }}</p><h2>{{ content.title }}</h2><strong class="&__amount" dir="ltr">{{ content.amount }}</strong><p class="&__state">{{ $t('Current status') }}: {{ currentStatus }}</p><p v-if="!['confirmed','completed'].includes(result.currentStatus)">{{ $t('Not a completed payment.') }}</p><p v-if="content.test">{{ $t('Test mode. Do not use real money.') }}</p><dl><div><dt>{{ $t('Status') }}</dt><dd>{{ content.status }}</dd></div><div v-for="row in content.rows" :key="row.label"><dt>{{ row.label }}</dt><dd>{{ row.value }}</dd></div><div><dt>{{ $t('Created') }}</dt><dd>{{ date(result.document.issuedAt) }}</dd></div><div><dt>{{ $t('Last updated {time}', { time: '' }) }}</dt><dd>{{ date(result.checkedAt) }}</dd></div></dl><p v-if="!result.currentStatus">{{ $t('Some activity could not be loaded.') }}</p></section><MwButton type="secondary" :isLoading="loading" @click="verify">{{ $t('Refresh') }}</MwButton></main>
</template>
<style lang="scss">
/* Hallmark · design-system: design.md · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } { max-width: 44rem; margin: 0 auto; padding: max(var(--space-lg), env(safe-area-inset-top)) var(--space-lg); color: var(--color-ink); min-height: 100dvh; overflow-wrap: anywhere;
  header { display: flex; align-items: center; gap: var(--space-md); font-weight: 600; } header svg { width: 4rem; height: 4rem; }
  h1 { margin-block: var(--space-xl); font-size: var(--text-title); } h2 { font-size: 1.25rem; font-weight: 600; }
  &__verified, dt { color: var(--color-muted); font-size: var(--text-label); } &__amount { display: block; font-size: clamp(1.75rem, 5vw, 2.5rem); font-variant-numeric: tabular-nums; }
  &__state { font-weight: 600; } dl > div { padding-block: var(--space-md); border-bottom: 1px solid var(--color-rule); display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: var(--space-md); }
  dd { margin: 0; white-space: pre-wrap; text-align: end; font-size: 0.875rem; } p { line-height: 1.6; } .mw-button { margin-top: var(--space-lg); }
}
</style>
