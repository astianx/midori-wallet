<script>
import { useId } from 'vue';
export default {
  setup() { return { panelId: `dropdown-${useId()}` }; },
  data() { return { expanded: false, placement: {} }; },
  mounted() {
    document.addEventListener('pointerdown', this.outside);
    window.addEventListener('resize', this.position);
    window.addEventListener('scroll', this.position, true);
  },
  beforeUnmount() {
    this.$refs.panel?.hidePopover?.();
    document.removeEventListener('pointerdown', this.outside);
    window.removeEventListener('resize', this.position);
    window.removeEventListener('scroll', this.position, true);
  },
  methods: {
    async toggle() {
      if (this.expanded) { this.close(); return; }
      this.expanded = true;
      await this.$nextTick();
      this.$refs.panel.showPopover?.();
      this.position();
      this.items()[0]?.focus({ preventScroll: true });
    },
    items() { return [...this.$refs.panel.querySelectorAll('button:not(:disabled),a[href]')]; },
    outside(event) { if (this.expanded && !this.$refs.trigger.contains(event.target) && !this.$refs.panel.contains(event.target)) this.close(false); },
    position() {
      if (!this.expanded) return;
      if (!this.$refs.trigger?.getClientRects().length) { this.close(false); return; }
      const anchor = this.$refs.trigger.getBoundingClientRect(), panel = this.$refs.panel.getBoundingClientRect();
      const viewport = window.visualViewport, width = viewport?.width || window.innerWidth, height = viewport?.height || window.innerHeight;
      const left = Math.min(Math.max(8, getComputedStyle(this.$el).direction === 'rtl' ? anchor.left : anchor.right - panel.width), width - panel.width - 8);
      const below = anchor.bottom + 8;
      const top = below + panel.height <= height - 8 ? below : Math.max(8, Math.min(anchor.top - panel.height - 8, height - panel.height - 8));
      this.placement = { left: `${left}px`, top: `${top}px` };
    },
    close(restore = true) {
      if (!this.expanded) return;
      this.$refs.panel.hidePopover?.(); this.expanded = false;
      if (restore) this.$refs.trigger.querySelector('button')?.focus({ preventScroll: true });
    },
    keydown(event) {
      if (event.key === 'Escape') { event.preventDefault(); this.close(); }
      if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      const items = this.items(), index = items.indexOf(document.activeElement);
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
      items[next]?.focus({ preventScroll: true });
    },
    selected(event) { if (event.target.closest('button:not(:disabled),a[href]')) this.close(false); },
  },
};
</script>
<template>
  <div class="&">
    <div ref="trigger" class="&__button" @click="toggle" @keydown.down.prevent="!expanded && toggle()"><slot name="button" :expanded="expanded" :panelId="panelId" /></div>
    <Teleport to="body"><div v-show="expanded" :id="panelId" ref="panel" popover="auto" role="dialog" :aria-label="$t('Menu')" class="&__panel" :style="placement" @toggle="expanded = $event.newState === 'open'" @keydown="keydown" @click="selected"><slot name="content" /></div></Teleport>
  </div>
</template>
<style lang="scss">
.#{ $filename } {
  flex: none; width: var(--spacing-5xl); height: 100%;
  &__button { width: 100%; height: 100%; }
  &__panel { position: fixed; z-index: var(--zindex-dropdown); inset: auto; width: min(22.5rem, calc(100vw - 1rem)); max-height: calc(100dvh - 1rem); overflow-y: auto; margin: 0; padding: 0; border: 0; border-radius: var(--radius-control); background: var(--color-modal-background); color: var(--color-text); box-shadow: var(--shadow-floating); }
}
</style>
