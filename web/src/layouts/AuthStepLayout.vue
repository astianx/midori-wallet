<script>
import MwNavbar from '../components/MwNavbar.vue';
import { onShowOnHide } from '../lib/mixins.js';

export default {
  components: {
    MwNavbar,
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
      <aside class="&__guide"><strong>Midori Wallet</strong><h1>{{ $t('Your wallet') }}</h1><p>{{ $t('Your keys. Your control.') }}</p><p>{{ $t('Your 12 words restore your wallet. You can also save this optional recovery file. It contains no private keys.') }}</p></aside>
      <div class="&__container">
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
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · pre-emit critique: P5 H5 E4 S5 R5 V4 */
.#{ $filename } { height: 100%; min-height: 0; display: flex; flex-direction: column; background: var(--color-paper); color: var(--color-ink); overflow-y: auto;
 &__frame { flex: 1; display: grid; grid-template-columns: minmax(0, 1fr); min-height: 0; }
 &__guide { display: none; }
 &__container { display: flex; flex-direction: column; min-height: 0; min-width: 0; background: var(--color-surface); padding-top: env(safe-area-inset-top); }
 &__content { display: flex; flex-direction: column; min-height: 0; flex: 1; overflow-y: auto; gap: var(--space-lg); padding: var(--space-lg) max(var(--space-lg), env(safe-area-inset-right)) max(var(--space-lg), env(safe-area-inset-bottom)) max(var(--space-lg), env(safe-area-inset-left)); }
 &__progress { display: grid; gap: var(--space-xs); color: var(--color-muted); font-size: var(--text-label); }
 progress { width: 100%; height: .25rem; accent-color: var(--color-accent); }
 @media(min-width: 64rem) { &__frame:not([data-runtime='phonegap']) { grid-template-columns: minmax(0, .85fr) minmax(0, 1fr); } &__frame:not([data-runtime='phonegap']) &__guide { display: flex; flex-direction: column; justify-content: center; gap: var(--space-lg); padding: var(--space-2xl); background: var(--color-brand-panel); color: var(--color-brand-panel-ink); } &__guide h1 { font-size: 2.5rem; letter-spacing: -.04em; } &__guide p { max-width: 32ch; line-height: 1.7; } &__container { padding-inline: max(var(--space-lg), calc((100% - 32rem) / 2)); } &__frame[data-runtime='phonegap'] &__container { padding-inline: 0; } }
}
</style>
