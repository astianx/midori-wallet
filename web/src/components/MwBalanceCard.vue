<script>
import MwAppIcon from './MwAppIcon.vue';
export default {
  components: { MwAppIcon },
  computed: {
    available() { return this.$cryptos.length > 0 && this.$cryptos.every(item => item.balance?.value !== undefined); },
    value() {
      if (this.$isHiddenBalance) return '••••';
      if (!this.available) return '—';
      const sum = this.$cryptos.reduce((total, item) => total + item.balance.value, 0n);
      const whole = new Intl.NumberFormat(this.$i18n.locale, { maximumFractionDigits: 0, numberingSystem: 'latn' }).format(sum / 1000000n);
      const separator = new Intl.NumberFormat(this.$i18n.locale, { numberingSystem: 'latn' }).formatToParts(1.1).find(part => part.type === 'decimal')?.value || '.';
      return `≈ ${whole}${separator}${(sum % 1000000n).toString().padStart(6, '0').slice(0, 2)}`;
    },
    fresh() { return this.available && this.$cryptos.every(item => item.readState === 'fresh'); },
  },
};
</script>
<template>
  <section class="&" :aria-label="$t('Balance')">
    <div class="&__top"><span>{{ $t('Balance') }}</span><button type="button" :aria-label="$t($isHiddenBalance ? 'Show balance' : 'Hide balance')" :aria-pressed="$isHiddenBalance" @click="$account.toggleHiddenBalance()"><MwAppIcon :name="$isHiddenBalance ? 'hide' : 'eye'" /></button></div>
    <div class="&__value" dir="ltr">{{ value }}<span v-if="available && !$isHiddenBalance">USD</span></div>
    <div class="&__status"><span :class="{ '&__dot': fresh }" />{{ fresh ? 'USDC + USDT · Base' : $t(available ? 'Last known balance' : 'Balance unavailable') }}</div>
    <div class="&__actions"><RouterLink :to="{ name: 'add-money' }" class="&__action &__action--primary"><MwAppIcon name="plus" />{{ $t('Add') }}</RouterLink><RouterLink :to="{ name: 'withdraw' }" class="&__action"><MwAppIcon name="send" />{{ $t('Withdraw') }}</RouterLink></div>
    <p class="&__reference">{{ $t('Reference: USD 1 per token. Live prices unavailable.') }}</p>
  </section>
</template>
<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } {
  min-width: 0; padding: var(--space-lg); border-radius: var(--radius-hero); background: var(--color-surface-tint); color: var(--color-ink);
  &__top { display: flex; justify-content: space-between; align-items: center; font-size: 0.875rem; font-weight: 500; }
  &__top button { display: grid; place-items: center; width: 2.75rem; height: 2.75rem; border-radius: var(--radius-control); }
  &__top svg { width: 1.25rem; height: 1.25rem; }
  &__value { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-sm); margin-block: var(--space-md) var(--space-xs); font: 550 var(--text-balance)/1.1 var(--font-numeric); letter-spacing: -0.05em; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
  &__value > span { font-size: 1rem; font-weight: 450; letter-spacing: 0; color: var(--color-muted); }
  &__status { display: flex; align-items: center; gap: var(--space-xs); font-size: var(--text-label); color: var(--color-muted); }
  &__dot { width: 0.375rem; height: 0.375rem; border-radius: 50%; background: var(--color-accent); }
  &__actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-sm); margin-block: var(--space-xl) var(--space-md); }
  &__action { display: flex; align-items: center; justify-content: center; min-width: 0; min-height: 3rem; padding: var(--space-sm) var(--space-xs); border-radius: var(--radius-control); gap: var(--space-xs); background: var(--color-surface); color: var(--color-ink); font-size: 0.875rem; font-weight: 600; white-space: normal; text-align: center; }
  &__action svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__action:hover { background: var(--color-surface-subtle); color: var(--color-ink); }
  &__action:active { transform: translateY(1px); }
  &__action--primary { background: var(--color-accent); color: var(--color-on-accent); }
  &__action--primary:hover { background: var(--color-accent-hover); color: var(--color-on-accent); }
  @media (max-width: 47.99rem) { padding: var(--space-md); &__action { flex-direction: column; min-height: 4rem; } }
  &__reference { max-width: 48ch; margin: 0; color: var(--color-muted); font-size: 0.75rem; line-height: 1.5; }
  @include breakpoint(lg) { padding: var(--space-xl); }
}
</style>
