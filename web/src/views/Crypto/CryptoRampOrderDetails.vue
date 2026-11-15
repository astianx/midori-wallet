<script>
import MwModal from '../../components/MwModal.vue';
import MwButton from '../../components/MwButton.vue';
import MwFormTextareaReadonly from '../../components/MwForm/MwFormTextareaReadonly.vue';
import { rampAmount, rampStatus, rampStatusMessage, rampNetwork } from '../../lib/account/ramp-presentation.js';
export default {
  components: { MwModal, MwButton, MwFormTextareaReadonly },
  props: { order: { type: Object, default: null }, isLoading: Boolean, error: { type: String, default: '' } },
  emits: ['close', 'refresh'],
  computed: {
    status() { return this.order && rampStatus(this.order, this.$t); },
    message() { return this.order && rampStatusMessage(this.order, this.$t); },
    network() { return rampNetwork(this.order?.environment); },
  },
  methods: { amount: rampAmount, date(value) { const date = new Date(value); return Number.isFinite(date.valueOf()) ? this.$d(date, 'long') : '—'; } },
};
</script>
<template>
  <MwModal :show="!!order" :title="$t('Order details')" @close="$emit('close')">
    <div v-if="order" class="&">
      <p :class="{ '&__status--negative': order.status === 'failed' }" class="&__status" role="status">{{ status }}</p><p class="&__message">{{ message }}</p>
      <p v-if="order.environment === 'STAGING'" class="&__message">{{ $t('Test mode. Do not use real money.') }}</p>
      <dl><div><dt>{{ $t('Network') }}</dt><dd>{{ network }}</dd></div><div><dt>{{ $t('You pay') }}</dt><dd dir="ltr">{{ order.kind === 'buy' ? amount(order.fiatAmount, order.fiatCurrency) : amount(order.cryptoAmount, order.cryptoCurrency) }}</dd></div><div><dt>{{ $t('Receive') }}</dt><dd dir="ltr">{{ order.kind === 'buy' ? amount(order.cryptoAmount, order.cryptoCurrency) : amount(order.fiatAmount, order.fiatCurrency) }}</dd></div><div><dt>{{ $t('Created') }}</dt><dd>{{ date(order.createdAt) }}</dd></div><div><dt>{{ $t('Last updated {time}', { time: '' }) }}</dt><dd>{{ date(order.updatedAt) }}</dd></div><div v-if="order.paymentMethod"><dt>{{ $t('Payment method') }}</dt><dd>{{ order.paymentMethod }}</dd></div></dl>
      <MwFormTextareaReadonly :label="$t('Order reference')" :value="order.id" copy />
      <details v-if="order.failureReason || order.transactionHash" class="&__technical"><summary>{{ $t('Details') }}</summary><p v-if="order.failureReason">{{ order.failureReason }}</p><MwFormTextareaReadonly v-if="order.transactionHash" :label="$t('Transaction ID')" :value="order.transactionHash" copy /></details>
      <p v-if="error" class="&__error" role="alert">{{ error }}</p>
    </div>
    <template #footer><MwButton class="&__refresh" type="secondary" :isLoading="isLoading" @click="$emit('refresh')">{{ $t('Refresh') }}</MwButton></template>
  </MwModal>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app · pre-emit critique: P5 H5 E4 S5 R5 V4 */
.#{ $filename } {
  min-width: 0;
  &__status { margin: 0; color: var(--color-accent); font-size: 1.125rem; font-weight: 600; }
  &__status--negative { color: var(--color-error); }
  &__message { color: var(--color-muted); font-size: var(--text-label); line-height: 1.6; }
  dl > div { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-md); padding-block: var(--space-xs); }
  dt { color: var(--color-muted); font-size: var(--text-label); }
  dd { margin: 0; min-width: 0; max-width: 65%; text-align: end; font-size: 0.9375rem; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
  &__technical { margin-top: var(--space-md); font-size: var(--text-label); overflow-wrap: anywhere; }
  summary { cursor: pointer; min-height: 2.75rem; align-content: center; }
  &__error { color: var(--color-error); font-size: var(--text-label); }
  &__refresh { width: 100%; }
}
</style>
