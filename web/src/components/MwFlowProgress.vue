<script>
export default { props: { current: { type: Number, default: 0 } } };
</script>
<template>
  <ol class="&" :aria-label="$t('Send')"><li v-for="(label, index) in ['To', 'Amount', 'Review payment']" :key="label" :aria-current="index === current ? 'step' : undefined" :class="{ '&__step--current': index === current, '&__step--complete': index < current }"><span class="&__number" aria-hidden="true">{{ index + 1 }}</span><span>{{ $t(label) }}</span></li></ol>
</template>
<style lang="scss">
.#{ $filename } {
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 0; padding: 0; list-style: none; gap: var(--space-sm);
  li { display: flex; align-items: center; gap: var(--space-xs); min-width: 0; font-size: 0.75rem; color: var(--color-muted); }
  &__number { display: grid; place-items: center; width: 1.5rem; height: 1.5rem; flex: none; border-radius: 50%; border: 1px solid var(--color-rule); background: var(--color-surface-subtle); }
  &__step--current { color: var(--color-accent) !important; font-weight: 600; }
  &__step--current &__number, &__step--complete &__number { background: var(--color-accent); color: var(--color-on-accent); border-color: var(--color-accent); }
  @media (max-width: 23rem) { li { flex-direction: column; align-items: flex-start; } }
}
</style>
