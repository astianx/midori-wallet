<script>
import { useId } from 'vue';
import CloseIcon from '../assets/svg/close.svg';

const openDialogs = [];
let nativeBack;
const dismissTop = () => openDialogs.at(-1)?.requestClose();

export default {
  components: { CloseIcon },
  props: { show: Boolean, title: { type: String, required: true }, description: { type: String, default: '' } },
  emits: ['close'],
  setup() { return { titleId: `dialog-${useId()}` }; },
  data() { return { previousFocus: null, registered: false }; },
  watch: { show: { immediate: true, async handler() { await this.$nextTick(); this.sync(); } } },
  mounted() { this.sync(); },
  beforeUnmount() { this.release(); },
  methods: {
    sync() {
      const dialog = this.$refs.dialog;
      if (!dialog) return;
      if (this.show && !dialog.open) {
        this.previousFocus = document.activeElement;
        if (!openDialogs.length && this.env.VITE_BUILD_TYPE === 'phonegap') { nativeBack = window.backButtonModal; window.backButtonModal = dismissTop; }
        openDialogs.push(this); this.registered = true;
        dialog.showModal();
        const target = this.$refs.body.querySelector('[autofocus],input:not(:disabled),textarea:not(:disabled),select:not(:disabled),button:not(:disabled),a[href],[tabindex="0"]') || this.$refs.footer?.querySelector('button:not(:disabled)') || dialog;
        target.focus({ preventScroll: true });
      } else if (!this.show) this.release();
    },
    release() {
      if (!this.registered) return;
      const wasTop = openDialogs.at(-1) === this;
      const index = openDialogs.indexOf(this);
      if (index >= 0) openDialogs.splice(index, 1);
      this.registered = false;
      for (const child of openDialogs) if (this.$refs.dialog?.contains(child.previousFocus)) child.previousFocus = this.previousFocus;
      this.$refs.dialog?.close();
      if (!openDialogs.length && window.backButtonModal === dismissTop) { if (nativeBack) window.backButtonModal = nativeBack; else delete window.backButtonModal; nativeBack = undefined; }
      if (wasTop && this.previousFocus?.isConnected) this.previousFocus.focus({ preventScroll: true });
    },
    containFocus(event) {
      if (event.key !== 'Tab' || openDialogs.at(-1) !== this) return;
      const nodes = [...this.$refs.dialog.querySelectorAll('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]')].filter(node => node.getClientRects().length);
      const first = nodes[0], last = nodes.at(-1);
      if (!first) { event.preventDefault(); this.$refs.dialog.focus(); return; }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === this.$refs.dialog)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || document.activeElement === this.$refs.dialog)) { event.preventDefault(); first.focus(); }
    },
    requestClose() { if (openDialogs.at(-1) === this) this.$emit('close'); },
    outside(event) {
      if (event.target !== this.$refs.dialog) return;
      const box = this.$refs.dialog.getBoundingClientRect();
      if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) this.requestClose();
    },
  },
};
</script>
<template>
  <Teleport to="body"><dialog ref="dialog" class="&__container" :aria-labelledby="titleId" :aria-describedby="description ? `${titleId}-description` : undefined" tabindex="-1" @keydown="containFocus" @cancel.prevent="requestClose" @click="outside" @close="show && requestClose()"><header class="&__header"><h2 :id="titleId" class="&__title">{{ title }}</h2><button type="button" class="&__close" :aria-label="$t('Close')" @click="requestClose"><CloseIcon aria-hidden="true" /></button></header><p v-if="description" :id="`${titleId}-description`">{{ description }}</p><div ref="body" class="&__body"><slot v-if="show" /></div><footer v-if="$slots.footer && show" ref="footer" class="&__footer"><slot name="footer" /></footer></dialog></Teleport>
</template>
<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P5 H5 E4 S4 R5 V4 */
.#{ $filename } {
  &__container { position: fixed; inset: 0; width: calc(100% - 2rem); max-width: 30rem; height: fit-content; max-height: calc(100dvh - 2rem); overflow-y: auto; margin: auto; padding: var(--space-lg); border: 0; border-radius: var(--radius-hero); background: var(--color-surface-raised); color: var(--color-ink); box-shadow: var(--shadow-floating); }
  &__container::backdrop { background: var(--color-overlay); }
  &__container[open] { animation: mw-dialog-appear var(--dur-short) var(--ease-out); }
  &__header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); margin-bottom: var(--space-lg); }
  &__title { margin: 0; font-size: 1.25rem; font-weight: 600; letter-spacing: -0.03em; }
  &__close { display: grid; place-items: center; flex: none; width: 2.75rem; height: 2.75rem; border-radius: var(--radius-control); background: var(--color-surface-subtle); color: var(--color-ink); }
  &__close svg { width: 1.125rem; height: 1.125rem; }
  &__body { display: flex; flex-direction: column; gap: var(--space-md); font-size: var(--text-body); }
  &__footer { margin-top: var(--space-lg); }
}
@keyframes mw-dialog-appear { from { opacity: 0; transform: translateY(0.5rem); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) { .mw-modal__container[open] { animation: none; } }
</style>
