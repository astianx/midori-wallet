<script>
import MwSidebar from '../components/MwSidebar.vue';
import MwAppIcon from '../components/MwAppIcon.vue';
export default {
  components: { MwSidebar, MwAppIcon },
  computed: {
    navigation() { return [{ name: 'home', icon: 'home', label: this.$t('Overview') }, { name: 'activity', icon: 'activity', label: this.$t('Activity') }, { name: 'receive', icon: 'receive', label: this.$t('Receive') }, { name: 'settings', icon: 'user', label: this.$t('Account') }]; },
    active() { const name = this.$route.name || ''; return name.includes('receive') ? 'receive' : name.includes('history') || name === 'activity' ? 'activity' : name.startsWith('settings') ? 'settings' : 'home'; },
    focusedFlow() { return /crypto\.(send|buy|sell|export|import)|settings\.(pin|hardware)/.test(this.$route.name || ''); },
  },
};
</script>
<template>
  <div class="&" :data-runtime="env.VITE_BUILD_TYPE" :class="{ '&--flow': focusedFlow }">
    <a class="&__skip" href="#wallet-content">{{ $t('Skip to content') }}</a><MwSidebar />
    <main id="wallet-content" class="&__content" tabindex="-1"><RouterView v-slot="{ Component, route }"><component :is="Component" :key="route.path + (route.meta.ts || '')" /></RouterView></main>
    <nav v-if="!focusedFlow" class="&__bottom" :aria-label="$t('Wallet')"><RouterLink v-for="item in navigation" :key="item.name" :to="{ name: item.name }" :class="{ '&__destination--active': active === item.name }" class="&__destination" :aria-current="active === item.name ? 'page' : undefined"><MwAppIcon :name="item.icon" /><span>{{ item.label }}</span></RouterLink></nav>
  </div>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app */
.#{ $filename } {
  display: flex; flex-direction: column; height: 100%; min-height: 0; padding: env(safe-area-inset-top) env(safe-area-inset-right) 0 env(safe-area-inset-left); background: var(--color-paper); color: var(--color-ink);
  &__content { flex: 1; min-height: 0; min-width: 0; overflow: clip; }
  &__skip { position: fixed; top: -6rem; inset-inline-start: var(--space-md); z-index: 600; padding: var(--space-md); background: var(--color-surface); color: var(--color-ink); }
  &__skip:focus { top: var(--space-md); }
  &__bottom { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); flex: none; padding: var(--space-xs) var(--space-xs) max(var(--space-xs), env(safe-area-inset-bottom)); border-top: 1px solid var(--color-rule); background: var(--color-surface); color: var(--color-muted); }
  &__destination { display: flex; min-width: 0; min-height: 3rem; align-items: center; flex-direction: column; justify-content: center; gap: var(--space-2xs); color: var(--color-muted); font-size: 0.6875rem; line-height: 1.3; white-space: nowrap; border-radius: var(--radius-control); }
  &__destination svg { width: 1.25rem; height: 1.25rem; }
  &__destination--active { color: var(--color-accent); background: var(--color-surface-tint); font-weight: 600; }
  @include breakpoint(lg) { flex-direction: row; &__content { padding: var(--space-lg); } &__bottom { display: none; } }
  &[data-runtime='phonegap'] { flex-direction: column; .mw-sidebar { display: none; } .app-layout__content { padding: 0; } .app-layout__bottom { display: grid; } }
}
</style>
