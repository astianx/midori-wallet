<script>
import { rampAmount, rampStatus } from '../../lib/account/ramp-presentation.js';
import MwAppIcon from '../../components/MwAppIcon.vue';
export default {
  components: { MwAppIcon },
  props: { order: { type: Object, required: true }, interactive: Boolean },
  emits: ['select'],
  computed: {
    description() { return this.$t(this.order.kind === 'buy' ? 'Buy {symbol}' : 'Sell {symbol}', { symbol: this.order.cryptoCurrency }); },
    amount() { return this.order.kind === 'buy' ? rampAmount(this.order.fiatAmount, this.order.fiatCurrency) : rampAmount(this.order.cryptoAmount, this.order.cryptoCurrency); },
    status() { return rampStatus(this.order, this.$t); },
    date() { const timestamp = new Date(this.order.updatedAt); return Number.isFinite(timestamp.valueOf()) ? this.$d(timestamp, timestamp.getFullYear() === new Date().getFullYear() ? 'shortCurrentYear' : 'short') : '—'; },
  },
};
</script>
<template>
  <component :is="interactive ? 'button' : 'div'" :type="interactive ? 'button' : undefined" class="&" :class="{ '&--interactive': interactive }" @click="interactive && $emit('select')">
    <div class="&__content"><div class="&__title"><span>{{ description }}</span><span class="&__amount" dir="ltr">{{ amount }}</span></div><div class="&__subtitle"><span class="&__status" :class="{ '&__status--positive': order.status === 'completed', '&__status--negative': order.status === 'failed' }">{{ status }}</span><span>{{ date }}</span></div><div class="&__provider">{{ order.provider }} · {{ order.environment }}<template v-if="order.environment === 'STAGING'"> · {{ $t('Test mode') }}</template></div></div><MwAppIcon v-if="interactive" name="arrow" />
  </component>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app · pre-emit critique: P5 H5 E4 S5 R5 V4 */
.#{ $filename } {
  display: flex; width: 100%; min-width: 0; align-items: center; gap: var(--space-sm); padding: var(--space-md) var(--space-xs); border-bottom: 1px solid var(--color-rule); color: var(--color-ink); text-align: start;
  &--interactive:hover { background: var(--color-surface-subtle); }
  &--interactive:active { background: var(--color-surface-tint); }
  &__content { flex: 1; min-width: 0; }
  &__title, &__subtitle { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: var(--space-xs) var(--space-sm); }
  &__title { font-size: 0.9375rem; font-weight: 550; }
  &__amount { min-width: 0; margin-inline-start: auto; overflow-wrap: anywhere; text-align: end; font-variant-numeric: tabular-nums; }
  &__subtitle, &__provider { color: var(--color-muted); font-size: var(--text-label); line-height: 1.6; }
  &__subtitle { margin-top: var(--space-2xs); }
  &__provider { margin-top: var(--space-xs); }
  &__status--positive { color: var(--color-accent); }
  &__status--negative { color: var(--color-error); }
  > svg { flex: none; width: 1rem; height: 1rem; }
}
</style>
