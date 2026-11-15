<script>
import MwLoader from '../components/MwLoader.vue';
import MwNavbar from '../components/MwNavbar.vue';
import { onShowOnHide } from '../lib/mixins.js';

export default {
  components: {
    MwLoader,
    MwNavbar,
  },
  mixins: [onShowOnHide],
  props: {
    allowReadFailure: { type: Boolean, default: false },
    wide: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      default: '',
    },
    description: {
      type: String,
      default: '',
    },
    onBack: {
      type: Function,
      default: undefined,
    },
  },
  data() {
    return {
      scrollTop: 0,
    };
  },
  onShow() {
    if (this.env.VITE_BUILD_TYPE === 'phonegap') {
      window.backButton = this.back;
    }
    this.$refs.container.scrollTop = this.scrollTop;
  },
  onHide() {
    if (this.env.VITE_BUILD_TYPE === 'phonegap') {
      if (window.backButton === this.back) delete window.backButton;
    }
    this.scrollTop = this.$refs.container.scrollTop;
  },
  methods: {
    back() {
      if (this.onBack) {
        this.onBack();
      } else if (this.$parent.$options.extends?.name === 'MwStep') {
        this.$parent.back();
      } else {
        this.$router.up();
      }
    },
  },
};
</script>

<template>
  <div class="&">
    <div class="&__navbar">
      <slot
        v-if="$slots.navbar"
        name="navbar"
      />
      <MwNavbar
        v-else
        :title="title"
        :description="description"
        @back="back"
      />
    </div>
    <div
      ref="container"
      class="&__container"
      data-transition
    >
      <div
        class="&__content"
        :class="{ '&__content--narrow': !wide }"
      >
        <template v-if="$wallet && !allowReadFailure">
          <MwLoader v-if="$walletState === $STATE_LOADING" />
          <slot v-if="$walletState === $STATE_LOADED" />
          <div
            v-if="$walletState === $STATE_ERROR"
            class="&__error"
          >
            {{ $account.unknownError() }}
          </div>
        </template>
        <slot v-else />
      </div>
    </div>
  </div>
</template>

<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P4 H5 E4 S5 R5 V4 */
  .#{ $filename } {
    display: flex;
    width: 100%;
    height: 100%;
    min-height: 0;
    min-width: 0;
    flex-direction: column;

    &__container {
      display: flex;
      flex: 1;
      min-height: 0;
      min-width: 0;
      flex-direction: column;
      align-items: center;
      overflow-y: auto;
      overscroll-behavior-y: contain;
      scroll-padding-block: var(--space-md);
      scrollbar-width: thin;
    }

    &__content {
      display: flex;
      width: 100%;
      flex: 1 0 auto;
      min-height: 100%;
      max-width: 68rem;
      flex-direction: column;
      padding: var(--space-lg) var(--space-md) max(var(--space-lg), env(safe-area-inset-bottom));
      gap: var(--space-lg);
      > * { flex-shrink: 0; min-width: 0; }
      @media (min-width: 40rem) { padding-inline: var(--space-xl); gap: var(--space-xl); }

      &--narrow {
        max-width: 34rem;
      }
    }

    &__navbar {
      flex: none;
      @include breakpoint(lg) {
        border-bottom: 1px solid var(--color-divider);
      }
    }

    &__error {
      @include text-md;
    }
  }
</style>
