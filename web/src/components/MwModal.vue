<script>
import CloseIcon from '../assets/svg/close.svg';
export default {
  components: { CloseIcon }, props: { show: Boolean, title: { type: String, required: true } }, emits: ['close'],
  data() { return { previousFocus: null }; },
  watch: {
    show: { immediate: true, async handler(show) {
      if (show) { this.previousFocus = document.activeElement; await this.$nextTick(); if (!this.show) return; this.$refs.dialog?.focus(); window.addEventListener('keydown', this.keydown); if (this.env.VITE_BUILD_TYPE === 'phonegap') window.backButtonModal = () => this.$emit('close'); }
      else { window.removeEventListener('keydown', this.keydown); if (this.env.VITE_BUILD_TYPE === 'phonegap') delete window.backButtonModal; this.previousFocus?.focus?.({ preventScroll: true }); }
    } },
  },
  beforeUnmount() { window.removeEventListener('keydown', this.keydown); if (this.env.VITE_BUILD_TYPE === 'phonegap') delete window.backButtonModal; },
  methods: {
    keydown(event) {
      if (event.key === 'Escape') { event.preventDefault(); this.$emit('close'); }
      if (event.key !== 'Tab') return;
      const elements = [...this.$refs.dialog.querySelectorAll('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]')].filter(el => el.getClientRects().length);
      const first = elements[0], last = elements.at(-1);
      if (!first) { event.preventDefault(); return; }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === this.$refs.dialog)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === this.$refs.dialog)) { event.preventDefault(); first.focus(); }
    },
  },
};
</script>
<template>
  <Teleport to="body"><Transition name="modal"><div v-if="show" class="&__overlay" @click.self="$emit('close')"><section ref="dialog" class="&__container" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1"><header class="&__header"><h2 class="&__title">{{ title }}</h2><button type="button" class="&__close" :aria-label="$t('Close')" @click="$emit('close')"><CloseIcon /></button></header><div class="&__body"><slot /></div><footer v-if="$slots.footer" class="&__footer"><slot name="footer" /></footer></section></div></Transition></Teleport>
</template>
<style lang="scss">
.#{ $filename } {
  &__overlay { position: fixed; z-index: var(--zindex-modal); inset: 0; display: flex; align-items: center; justify-content: center; padding: var(--space-md); overflow-y: auto; background: var(--color-overlay); color: var(--color-ink); }
  &__container { width: 100%; max-width: 30rem; max-height: calc(100dvh - 2rem); overflow-y: auto; margin: auto; padding: var(--space-lg); border-radius: var(--radius-hero); background: var(--color-surface-raised); color: var(--color-ink); box-shadow: var(--shadow-floating); }
  &__header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-lg); }
  &__title { margin: 0; font-size: 1.25rem; font-weight: 600; letter-spacing: -0.03em; }
  &__close { display: grid; place-items: center; flex: none; width: 2.75rem; height: 2.75rem; border-radius: var(--radius-control); background: var(--color-surface-subtle); color: var(--color-ink); }
  &__close svg { width: 1.125rem; height: 1.125rem; }
  &__body { display: flex; flex-direction: column; gap: var(--space-md); font-size: var(--text-body); }
  &__footer { margin-top: var(--space-lg); }
}
</style>
