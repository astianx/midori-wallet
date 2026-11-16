<script>
export default { props: { current: { type: Number, default: 0 } }, computed: { labels() { return ['To', 'Amount', 'Review payment', 'PIN', 'Status']; } } };
</script>
<template>
  <div class="&"><p class="&__current">{{ current + 1 }} / {{ labels.length }} · {{ $t(labels[current]) }}</p><ol :aria-label="$t('Send')"><li v-for="(label, index) in labels" :key="label" :aria-label="$t(label)" :aria-current="index === current ? 'step' : undefined" :class="{ '&__step--current': index === current, '&__step--complete': index < current }"><span class="&__number" aria-hidden="true">{{ index + 1 }}</span><span class="&__label" aria-hidden="true">{{ $t(label) }}</span></li></ol></div>
</template>
<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } {
  &__current { margin: 0 0 var(--space-sm); color: var(--color-muted); font-size: var(--text-label); }
  ol { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin: 0; padding: 0; list-style: none; gap: var(--space-xs); }
  li { display: flex; align-items: center; gap: var(--space-xs); min-width: 0; font-size: 0.75rem; color: var(--color-muted); }
  &__number { display: grid; place-items: center; width: min(1.5rem, 100%); height: auto; aspect-ratio: 1; flex: none; border-radius: 50%; border: 1px solid var(--color-rule); background: var(--color-surface-subtle); }
  &__label { display: none; }
  &__step--current { color: var(--color-accent) !important; font-weight: 600; }
  &__step--current &__number, &__step--complete &__number { background: var(--color-accent); color: var(--color-on-accent); border-color: var(--color-accent); }
  @media (min-width: 48rem) { &__current { display: none; } &__label { display: block; } }
}
</style>
