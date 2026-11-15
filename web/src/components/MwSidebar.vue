<script>
import { formatMidoriTag } from '../lib/tags.js';
import LogoIcon from '../assets/svg/logo.svg';
import MwAppIcon from './MwAppIcon.vue';
export default {
  methods: { formatMidoriTag },
  components: { LogoIcon, MwAppIcon },
  computed: {
    items() { return [{ name: 'home', icon: 'home', label: this.$t('Overview') }, { name: 'activity', icon: 'activity', label: this.$t('Activity') }, { name: 'receive', icon: 'receive', label: this.$t('Receive') }, { name: 'settings', icon: 'user', label: this.$t('Account') }]; },
    current() { const name = this.$route.name || ''; if (name.includes('receive')) return 'receive'; if (name.includes('history') || name === 'activity') return 'activity'; if (name.startsWith('settings')) return 'settings'; return 'home'; },
  },
};
</script>
<template>
  <aside class="&">
    <RouterLink :to="{ name: 'home' }" class="&__brand" aria-label="Midori Wallet"><LogoIcon /></RouterLink>
    <nav class="&__nav" :aria-label="$t('Wallet')"><RouterLink v-for="item in items" :key="item.name" :to="{ name: item.name }" class="&__link" :class="{ '&__link--active': current === item.name }" :aria-current="current === item.name ? 'page' : undefined"><MwAppIcon :name="item.icon" /><span>{{ item.label }}</span></RouterLink></nav>
    <div class="&__network"><span class="&__network-dot" /><div>Base<small>USDC · USDT</small></div></div>
    <RouterLink :to="{ name: 'settings' }" class="&__profile"><span class="&__avatar"><MwAppIcon name="user" /></span><span>{{ $user.username ? formatMidoriTag($account.tag?.alias || $user.username) : $t('Your wallet') }}<small>{{ $t('Account') }}</small></span><MwAppIcon name="arrow" /></RouterLink>
  </aside>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app */
.#{ $filename } {
  display: none; min-width: 0; flex-direction: column; background: var(--color-surface); color: var(--color-ink); border-inline-end: 1px solid var(--color-rule); padding: var(--space-xl) var(--space-md); gap: var(--space-xl);
  &__brand { display: block; padding: var(--space-xs) var(--space-md); }
  &__brand svg { width: 8.5rem; height: auto; }
  &__nav { display: flex; flex-direction: column; gap: var(--space-xs); }
  &__link { display: flex; align-items: center; gap: var(--space-sm); min-height: 3rem; padding: var(--space-sm) var(--space-md); border-radius: var(--radius-control); color: var(--color-muted); font-size: 0.875rem; white-space: nowrap; }
  &__link svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__link:hover, &__link--active { background: var(--color-surface-tint); color: var(--color-accent); }
  &__link--active { font-weight: 600; }
  &__network { display: flex; gap: var(--space-sm); align-items: center; margin-top: auto; padding: var(--space-md); font-size: var(--text-label); color: var(--color-muted); }
  &__network-dot { width: 0.5rem; height: 0.5rem; border-radius: 50%; background: var(--color-accent); }
  &__network small, &__profile small { display: block; font-size: 0.6875rem; color: var(--color-muted); margin-top: var(--space-2xs); }
  &__profile { display: flex; align-items: center; gap: var(--space-sm); padding: var(--space-md) var(--space-xs); border-top: 1px solid var(--color-rule); color: var(--color-ink); font-size: var(--text-label); }
  &__profile > span:nth-child(2) { flex: 1; min-width: 0; overflow-wrap: anywhere; }
  &__profile svg { width: 1.125rem; height: 1.125rem; }
  &__avatar { display: grid; place-items: center; width: 2.25rem; height: 2.25rem; border-radius: var(--radius-control); background: var(--color-surface-subtle); color: var(--color-muted); }
  @include breakpoint(lg) { display: flex; width: 13.5rem; flex: 0 0 13.5rem; }
}
</style>
