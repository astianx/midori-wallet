<script>
import MwNavbar from '../components/MwNavbar.vue';
import LogoIcon from '../../../assets/brand/logo.svg';
import LogoOnDark from '../../../assets/brand/logo-on-dark.svg';
import { onShowOnHide } from '../lib/mixins.js';

export default {
  components: {
    MwNavbar,
    LogoIcon,
    LogoOnDark,
  },
  mixins: [onShowOnHide],
  props: {
    stepNumber: { type: Number, default: 0 },
    stepCount: { type: Number, default: 3 },
    title: {
      type: String,
      default: '',
    },
    description: {
      type: String,
      default: '',
    },
    showBack: {
      type: Boolean,
      default: true,
    },
    centered: {
      type: Boolean,
      default: false,
    },
    onBack: {
      type: Function,
      default: undefined,
    },
    faded: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      scrollTop: 0,
    };
  },
  onShow() {
    if (this.env.VITE_BUILD_TYPE === 'phonegap' && this.showBack) {
      window.backButton = this.back;
    }
    this.$refs.content.scrollTop = this.scrollTop;
  },
  onHide() {
    if (this.env.VITE_BUILD_TYPE === 'phonegap' && this.showBack) {
      if (window.backButton === this.back) delete window.backButton;
    }
    this.scrollTop = this.$refs.content.scrollTop;
  },
  methods: {
    back() {
      if (!this.showBack) return;
      if (this.onBack) {
        this.onBack();
      } else {
        this.$parent.back();
      }
    },
  },
};
</script>

<template>
  <div
    class="&"
    :class="{
      ['&--faded']: faded,
    }"
  >
    <div class="&__frame" :data-runtime="env.VITE_BUILD_TYPE">
      <aside class="&__guide">
        <LogoOnDark class="&__logo" role="img" aria-label="Midori Wallet" />
        <div class="&__message">
          <h1>{{ $t('Your wallet') }}</h1>
          <p>{{ $t('Send and receive USDC and USDT with your own wallet.') }}</p>
        </div>
        <p class="&__ownership">{{ $t('Your keys. Your control.') }}</p>
      </aside>
      <div class="&__container">
        <div class="&__brand"><component :is="$resolvedTheme === 'dark' ? 'LogoOnDark' : 'LogoIcon'" class="&__logo" role="img" aria-label="Midori Wallet" /></div>
        <MwNavbar
          :title="title"
          :description="description"
          :showBack="showBack"
          :centered="centered"
          @back="back"
        />
        <div
          ref="content"
          class="&__content"
          data-transition
        >
          <div v-if="stepNumber" class="&__progress" role="status"><span>{{ $t('Step {current} of {total}', { current: stepNumber, total: stepCount }) }}</span><progress :value="stepNumber" :max="stepCount" :aria-label="$t('Next step')" /></div>
          <slot />
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app
 * pre-emit critique: P5 H5 E5 S5 R5 V4 · centered access, compact PIN, existing brand assets */
body .#{ $filename } {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 0;
  padding: 0;
  overflow-y: auto;
  background: var(--color-paper);
  color: var(--color-ink);

  .#{ $filename }__frame {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
    flex: none;
    width: 100%;
    height: 100%;
    min-height: 0;
    max-width: none;
    margin: auto;
    border: 0;
    border-radius: 0;
    overflow: clip;
    box-shadow: none;
  }

  .#{ $filename }__guide { display: none; }
  .#{ $filename }__logo { display: block; width: 8rem; max-width: 100%; height: auto; flex: none; }
  .#{ $filename }__brand { display: flex; justify-content: center; padding: var(--space-lg) var(--space-lg) var(--space-xs); }
  .#{ $filename }__container {
    display: flex;
    flex-direction: column;
    width: 100%;
    min-width: 0;
    min-height: 0;
    max-width: none;
    padding: env(safe-area-inset-top) 0 0;
    background: var(--color-surface);
  }
  .#{ $filename }__container > .mw-navbar { flex: none; padding-inline: var(--space-lg); }
  .#{ $filename }__content {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
    gap: var(--space-lg);
    overflow-y: auto;
    overscroll-behavior-y: contain;
    padding: var(--space-lg) max(var(--space-lg), env(safe-area-inset-right)) max(var(--space-lg), env(safe-area-inset-bottom)) max(var(--space-lg), env(safe-area-inset-left));
  }
  .#{ $filename }__content:has(> .mw-pin__keyboard) { justify-content: safe center; }
  .#{ $filename }__content .mw-pin__dots { flex: none; min-height: 3rem; }
  .#{ $filename }__content .mw-pin__keyboard { width: min(100%, 18rem); }
  .#{ $filename }__content .mw-pin__keyboard .mw-pin__key { width: min(3.5rem, 100%); }
  .#{ $filename }__progress { display: grid; gap: var(--space-xs); color: var(--color-muted); font-size: var(--text-label); }
  progress { width: 100%; height: .25rem; accent-color: var(--color-accent); }

  @media (min-width: 48rem) {
    padding: var(--space-lg);
    .#{ $filename }__frame:not([data-runtime='phonegap']) {
      max-width: 36rem;
      height: min(44rem, 100%);
      border: 1px solid var(--color-rule);
      border-radius: var(--radius-hero);
      box-shadow: var(--shadow-panel);
    }
  }
  @media (min-width: 64rem) {
    padding: var(--space-xl);
    .#{ $filename }__frame:not([data-runtime='phonegap']) { grid-template-columns: minmax(0, .85fr) minmax(0, 1fr); max-width: 60rem; }
    .#{ $filename }__frame:not([data-runtime='phonegap']) .#{ $filename }__guide {
      display: flex;
      flex-direction: column;
      gap: var(--space-xl);
      min-width: 0;
      min-height: 0;
      overflow-y: auto;
      padding: var(--space-2xl);
      background: var(--color-brand-panel);
      color: var(--color-brand-panel-ink);
    }
    .#{ $filename }__message { margin-block: auto; }
    .#{ $filename }__message h1 { margin: 0 0 var(--space-lg); font-size: 2.5rem; font-weight: 550; line-height: 1.15; letter-spacing: -.04em; }
    .#{ $filename }__message p { max-width: 30ch; margin: 0; line-height: 1.7; color: var(--color-brand-panel-muted); }
    .#{ $filename }__ownership { margin: 0; font-size: var(--text-label); color: var(--color-brand-panel-muted); }
    .#{ $filename }__frame:not([data-runtime='phonegap']) .#{ $filename }__brand { display: none; }
    .#{ $filename }__content { padding: var(--space-xl); }
  }
  &:has(> [data-runtime='phonegap']) { padding: 0; }
}
</style>
