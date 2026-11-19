<script>
import layoutViewport from '../lib/layoutViewport.js';

import MwSidebar from '../components/MwSidebar.vue';

export default {
  mixins: [layoutViewport],
  components: {
    MwSidebar,
  },
};
</script>

<template>
  <div class="&" :data-runtime="env.VITE_BUILD_TYPE" :style="{ height: `${viewportHeight}px`, top: `${viewportOffset}px` }">
    <a class="&__skip" href="#wallet-content">{{ $t('Skip to content') }}</a>
    <MwSidebar :active="$route.name === 'home'" />
    <main
      id="wallet-content"
      tabindex="-1"
      class="&__content"
      :class="{ '&__content--active': $route.name !== 'home' }"
    >
      <RouterView v-slot="{ Component, route }">
        <component
          :is="Component"
          :key="route.meta.activityView ? route.name : route.fullPath + (route.meta.viewRevision || 0)"
        />
      </RouterView>
    </main>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    position: relative;
    display: flex;
    min-height: 0;
    height: 100%;
    padding:
      env(safe-area-inset-top)
      env(safe-area-inset-right)
      0
      env(safe-area-inset-left);
    gap: var(--spacing-md);

    @include breakpoint(lg) {
      max-width: var(--desktop-max-width);
      padding:
        max(var(--spacing-md), env(safe-area-inset-top))
        max(var(--spacing-md), env(safe-area-inset-right))
        max(var(--spacing-md), env(safe-area-inset-bottom))
        max(var(--spacing-md), env(safe-area-inset-left));
      margin: 0 auto;
    }

    &__skip { position: fixed; top: -6rem; z-index: 600; padding: var(--spacing-md); background: var(--color-background); }
    &__skip:focus { top: var(--spacing-md); }

    &__content {
      min-width: 0;
      min-height: 0;
      display: none;
      overflow: hidden;
      width: 100%;
      background-color: var(--color-background);
      @include breakpoint(lg) {
        display: block;
        border-radius: 0.625rem;
      }

      &--active {
        display: block;
      }
    }
    &[data-runtime='phonegap'] {
      max-width: none;
      padding: env(safe-area-inset-top) env(safe-area-inset-right) 0 env(safe-area-inset-left);
      > .mw-sidebar:not(.mw-sidebar--active) { display: none; }
      > .mw-sidebar--active { display: flex; width: 100%; border-radius: 0; }
      > .app-layout__content { display: none; border-radius: 0; }
      > .app-layout__content--active { display: block; }
    }
  }
</style>
