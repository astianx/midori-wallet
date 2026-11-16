<script>
import MwModal from './MwModal.vue';
import MwButton from './MwButton.vue';
import { activityTitle, activityStatus, activityMessage, activityGroup, activityRows } from '../lib/account/activity-presentation.js';
import { activityReader } from '../lib/account/activity-reader.js';
import { renderReceipt } from '../lib/receipt-document.js';
import { deliverReceiptFile } from '../lib/receipt-files.js';
export default {
  components: { MwModal, MwButton },
  props: { show: Boolean, record: { type: Object, default: null }, loading: Boolean, error: { type: String, default: '' } },
  emits: ['close', 'refresh'],
  data() { return { files: null, imageUrl: '', generating: false, sharing: false, exportError: '', exportGeneration: 0 }; },
  computed: {
    title() { return this.record ? activityTitle(this.record, this.$t) : this.$t('View receipt'); },
    rows() { return this.record ? activityRows(this.record, this.$t, value => this.date(value)).filter(row => !row.technical) : []; },
    technicalRows() { return this.record ? activityRows(this.record, this.$t, value => this.date(value)).filter(row => row.technical) : []; },
    status() { return this.record ? activityStatus(this.record, this.$t) : ''; },
    message() { return this.record ? activityMessage(this.record, this.$t) : ''; },
    tone() { return this.record ? activityGroup(this.record) : ''; },
    runtime() { return this.env.VITE_BUILD_TYPE; },
  },
  watch: { 'record.id'() { this.resetExport(); }, show() { this.resetExport(); }, '$isHiddenBalance'() { this.resetExport(); } },
  beforeUnmount() { this.resetExport(); },
  methods: {
    date(value) { const date = new Date(value); return Number.isFinite(date.valueOf()) ? this.$d(date, 'short') || new Intl.DateTimeFormat(this.$i18n.locale, { dateStyle: 'medium', timeStyle: 'short' }).format(date) : '—'; },
    resetExport() { this.exportGeneration++; if (this.imageUrl) URL.revokeObjectURL(this.imageUrl); this.files = null; this.imageUrl = ''; this.generating = false; this.sharing = false; this.exportError = ''; },
    async reviewShare() {
      if (this.generating || this.$isHiddenBalance || !this.record) return;
      const generation = ++this.exportGeneration; this.generating = true; this.exportError = '';
      try {
        const issued = await activityReader(this.$account).issueReceipt(this.record);
        const files = await renderReceipt(issued.document, issued.verificationUrl, this.$t, this.$i18n.locale);
        if (generation !== this.exportGeneration || !this.show || this.$isHiddenBalance) return;
        this.files = files; this.imageUrl = URL.createObjectURL(files.preview);
        this.$nextTick(() => this.$refs.share?.$el.focus({ preventScroll: true }));
      } catch { if (generation === this.exportGeneration) this.exportError = this.$t('Could not generate the receipt. Try again.'); }
      finally { if (generation === this.exportGeneration) this.generating = false; }
    },
    async share(download = false) {
      if (this.sharing || !this.files || this.$isHiddenBalance) return;
      this.sharing = true; this.exportError = ''; const generation = this.exportGeneration;
      try { await deliverReceiptFile(this.files.pdf, this.runtime, !download); }
      catch { if (generation === this.exportGeneration) this.exportError = this.$t('Could not save or share this file. Try again.'); }
      finally { if (generation === this.exportGeneration) this.sharing = false; }
    },
    refresh() { this.resetExport(); this.$emit('refresh'); },
  },
};
</script>
<template>
  <MwModal :show="show" :title="title" @close="$emit('close')">
    <div class="&" :data-runtime="runtime" :data-state="record?.state">
      <p v-if="loading" role="status">{{ $t('Loading') }}</p>
      <template v-if="record">
        <strong class="&__amount" dir="ltr">{{ $isHiddenBalance ? '••••' : record.amount }}</strong>
        <p class="&__status" :data-tone="tone" role="status">{{ status }}</p><p class="&__message">{{ message }}</p>
        <p v-if="record.environment === 'STAGING'" class="&__message">{{ $t('Test mode. Do not use real money.') }}</p>
        <dl><div v-for="row in rows.filter(item => item.label !== $t('Status'))" :key="row.label"><dt>{{ row.label }}</dt><dd :dir="row.financial ? 'ltr' : undefined">{{ row.financial && $isHiddenBalance ? '••••' : row.value }}</dd></div></dl>
        <details class="&__technical"><summary>{{ $t('Details') }}</summary><dl><div v-for="row in technicalRows" :key="row.label"><dt>{{ row.label }}</dt><dd>{{ row.value }}</dd></div><template v-if="record.transactionHash"><dt>{{ $t('Transaction ID') }}</dt><dd>{{ record.transactionHash }}</dd></template><template v-if="record.updatedAt"><dt>{{ $t('Last updated {time}', { time: '' }) }}</dt><dd>{{ date(record.updatedAt) }}</dd></template></dl><MwButton v-if="record.url" type="primary-link" @click="$safeOpen(record.url)">{{ $t('Explorer') }}</MwButton></details>
        <MwButton v-if="!files && !$isHiddenBalance" type="secondary" :isLoading="generating" @click="reviewShare">{{ $t('Share') }}</MwButton>
        <section v-if="files && !$isHiddenBalance" class="&__preview"><h3>{{ $t('Review before sharing') }}</h3><p>{{ $t('Anyone with this QR can view these details.') }}</p><figure><img :src="imageUrl" :alt="$t('Receipt')" /><figcaption>{{ $t('Scan to verify') }}</figcaption></figure><details><summary>{{ $t('Details') }}</summary><dl><div v-for="row in files.content.rows" :key="row.label"><dt>{{ row.label }}</dt><dd>{{ row.value }}</dd></div></dl></details><div class="&__actions"><MwButton ref="share" type="primary" :isLoading="sharing" @click="share()">{{ $t(runtime === 'electron' ? 'Save' : 'Share') }}</MwButton><MwButton v-if="runtime !== 'electron'" type="secondary" :disabled="sharing" @click="share(true)">{{ $t('Save') }}</MwButton></div></section>
        <p v-if="exportError" class="&__error" role="alert">{{ exportError }}</p>
      </template>
      <p v-if="error" class="&__error" role="alert">{{ error }}</p>
    </div>
    <template #footer><MwButton v-if="!files && !generating && (!record || ['pending','checking'].includes(tone) || error)" type="secondary" :isLoading="loading" @click="refresh">{{ $t('Refresh') }}</MwButton></template>
  </MwModal>
</template>
<style lang="scss">
/* Hallmark · design-system: design.md · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } {
  min-width: 0;
  &__amount { display: block; font-size: clamp(1.5rem, 4vw, 2rem); font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
  &__status { color: var(--color-warning); font-weight: 600; margin-block: var(--space-sm); }
  &__status[data-tone='completed'] { color: var(--color-accent); }
  &__status[data-tone='failed'] { color: var(--color-error); }
  &__message, dt { color: var(--color-muted); font-size: var(--text-label); line-height: 1.6; }
  dl { margin-block: var(--space-lg); font-size: 0.875rem; }
  dl > div { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 2fr); gap: var(--space-md); padding-block: var(--space-sm); border-bottom: 1px solid var(--color-rule); }
  dd { margin: 0; text-align: end; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
  &__technical summary { cursor: pointer; min-height: 2.75rem; align-content: center; font-size: 0.875rem; }
  &__technical dd { text-align: start; }
  &__preview { border-top: 1px solid var(--color-rule); padding-top: var(--space-lg); margin-top: var(--space-lg); }
  &__preview h3 { margin: 0; font-size: 1rem; }
  &__preview p { font-size: var(--text-label); }
  &__preview figure { margin: var(--space-md) 0; }
  &__preview img { display: block; width: 100%; height: auto; }
  &__preview figcaption { color: var(--color-muted); font-size: var(--text-label); margin-top: var(--space-xs); }
  &__preview summary { min-height: 2.75rem; align-content: center; cursor: pointer; }
  &__preview dd { white-space: pre-wrap; }
  &__actions { display: flex; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-sm); }
  &__error { color: var(--color-error); font-size: var(--text-label); }
}
@media (max-width: 47.99rem) { body:has(.mw-activity-receipt) .mw-modal__container { inset: 0; margin: 0; width: 100%; max-width: none; height: 100%; max-height: 100dvh; box-sizing: border-box; border-radius: 0; padding: max(var(--space-lg), env(safe-area-inset-top)) max(var(--space-lg), env(safe-area-inset-right)) max(var(--space-lg), env(safe-area-inset-bottom)) max(var(--space-lg), env(safe-area-inset-left)); } }
body:has(.mw-activity-receipt[data-runtime='phonegap']) .mw-modal__container { inset: 0; margin: 0; width: 100%; max-width: none; height: 100%; max-height: 100dvh; box-sizing: border-box; border-radius: 0; padding: max(var(--space-lg), env(safe-area-inset-top)) max(var(--space-lg), env(safe-area-inset-right)) max(var(--space-lg), env(safe-area-inset-bottom)) max(var(--space-lg), env(safe-area-inset-left)); }
</style>
