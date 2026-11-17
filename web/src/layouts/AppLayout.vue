<script>
import MwSidebar from '../components/MwSidebar.vue';
import layoutViewport from '../lib/layoutViewport.js';
import MwAppIcon from '../components/MwAppIcon.vue';
export default {
  components: { MwSidebar, MwAppIcon },
  mixins: [layoutViewport],
  computed: {
    navigation() { return [{ name: 'home', icon: 'home', label: this.$t('Overview') }, { name: 'activity', icon: 'activity', label: this.$t('Activity') }, { name: 'receive', icon: 'receive', label: this.$t('Receive') }, { name: 'settings', icon: 'user', label: this.$t('Account') }]; },
    active() { const name = this.$route.name || ''; if (this.$route.meta.navigation) return this.$route.meta.navigation; return name.includes('receive') ? 'receive' : name.includes('history') || name === 'activity' ? 'activity' : name.startsWith('settings') ? 'settings' : 'home'; },
    focusedFlow() { return Boolean(this.$route.meta.focusedFlow); },
  },
};
</script>
<template>
  <div class="&" :data-runtime="env.VITE_BUILD_TYPE" :class="{ '&--flow': focusedFlow }" :data-keyboard="keyboardOpen || undefined" :style="{ height: `${viewportHeight}px`, top: `${viewportOffset}px` }">
    <a class="&__skip" href="#wallet-content">{{ $t('Skip to content') }}</a><MwSidebar />
    <main id="wallet-content" class="&__content" tabindex="-1"><RouterView v-slot="{ Component, route }"><component :is="Component" :key="route.meta.activityView ? route.name : route.fullPath + (route.meta.viewRevision || 0)" /></RouterView></main>
    <nav v-if="!focusedFlow" class="&__bottom" :aria-label="$t('Wallet')"><RouterLink v-for="item in navigation" :key="item.name" :to="{ name: item.name }" :class="{ '&__destination--active': active === item.name }" class="&__destination" :title="item.label" :aria-current="active === item.name ? 'page' : undefined"><MwAppIcon :name="item.icon" /><span>{{ item.label }}</span></RouterLink></nav>
  </div>
</template>
<style lang="scss">
/* Hallmark · pre-emit critique: P4 H5 E4 S5 R5 V4 · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app */
.#{ $filename } {
  position: relative; display: flex; flex-direction: column; height: 100%; min-height: 0; padding: env(safe-area-inset-top) env(safe-area-inset-right) 0 env(safe-area-inset-left); background: var(--color-paper); color: var(--color-ink);
  &[data-keyboard] &__bottom { display: none; }
  > .mw-sidebar { overflow-y: auto; }
  &__content { flex: 1; min-height: 0; min-width: 0; overflow: clip; }
  &__skip { position: fixed; top: -6rem; inset-inline-start: var(--space-md); z-index: 600; padding: var(--space-md); background: var(--color-surface); color: var(--color-ink); }
  &__skip:focus { top: var(--space-md); }
  &__bottom { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); flex: none; padding: var(--space-xs) var(--space-xs) max(var(--space-xs), env(safe-area-inset-bottom)); border-top: 1px solid var(--color-rule); background: var(--color-surface); color: var(--color-muted); }
  &__destination { display: flex; min-width: 0; min-height: 3rem; align-items: center; flex-direction: column; justify-content: center; gap: var(--space-2xs); color: var(--color-muted); font-size: 0.6875rem; line-height: 1.3; white-space: normal; text-align: center; border-radius: var(--radius-control); }
  &__destination span { max-width: 100%; overflow-wrap: anywhere; }
  &__destination svg { width: 1.25rem; height: 1.25rem; }
  &__destination--active { color: var(--color-accent); background: var(--color-surface-tint); font-weight: 600; }
  @include breakpoint(lg) { flex-direction: row; &__content { padding: var(--space-lg); } &__bottom { display: none; } }
  &[data-runtime='phonegap'] { flex-direction: column; .mw-sidebar { display: none; } .app-layout__content { padding: 0; } .main-layout, .smart-layout { border-radius: 0; } .app-layout__bottom { display: grid; padding-bottom: max(var(--space-xs), env(safe-area-inset-bottom)); } &[data-keyboard] .app-layout__bottom { display: none; } }
}
</style>
