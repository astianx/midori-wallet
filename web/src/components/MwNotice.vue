<script>
import MwLoader from './MwLoader.vue';
import MwAppIcon from './MwAppIcon.vue';
export default {
  components: { MwLoader, MwAppIcon },
  props: { kind: { type: String, default: 'info', validator: value => ['info', 'loading', 'empty', 'warning', 'error', 'success'].includes(value) }, title: { type: String, default: '' }, message: { type: String, default: '' }, live: { type: Boolean, default: true } },
  computed: { announcement() { return this.live ? (this.kind === 'error' ? 'alert' : 'status') : undefined; } },
};
</script>
<template>
  <section class="&" :class="`&--${kind}`" :role="announcement" :aria-busy="kind === 'loading' || undefined"><MwLoader v-if="kind === 'loading'" aria-hidden="true" /><MwAppIcon v-else :name="kind === 'success' ? 'check' : kind === 'empty' ? 'activity' : 'help'" /><div class="&__body"><h3 v-if="title">{{ title }}</h3><p v-if="message">{{ message }}</p><slot /></div><div v-if="$slots.action" class="&__action"><slot name="action" /></div></section>
</template>
<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P4 H5 E4 S4 R5 V4 */
.#{ $filename } { display: flex; align-items: flex-start; flex-wrap: wrap; gap: var(--space-sm); padding: var(--space-md); border: 1px solid var(--color-rule); border-radius: var(--radius-control); background: var(--color-surface-subtle); color: var(--color-ink);
  > svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__body { flex: 1; min-width: min(10rem, 100%); }
  h3 { margin: 0 0 var(--space-xs); font-size: 0.9375rem; font-weight: 600; }
  p { margin: 0; font-size: 0.875rem; overflow-wrap: anywhere; }
  &--error { background: var(--color-error-surface); color: var(--color-error); border-color: var(--color-error); }
  &--warning { background: var(--color-warning-surface); color: var(--color-warning); }
  &--success { background: var(--color-surface-tint); color: var(--color-accent); }
}
</style>
