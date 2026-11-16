<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwPin from '../../components/MwPin.vue';
import MwStep from '../../components/MwStep.vue';
import { onShowOnHide, redirectToApp } from '../../lib/mixins.js';

export default {
  components: {
    AuthStepLayout,
    MwPin,
  },
  extends: MwStep,
  mixins: [onShowOnHide, redirectToApp],
  data() {
    return {
      isReady: this.env.VITE_PLATFORM !== 'android',
    };
  },
  onShow() {
    if (this.env.VITE_PLATFORM === 'android') {
      setTimeout(() => {
        this.isReady = true;
        window.systemBars.setStyle(this.$resolvedTheme === 'light' ? 'light' : 'dark');
      }, 500);
    }
  },
  onHide() {
    if (this.$refs.pin) this.$refs.pin.value = '';
  },
  methods: {
    async success() {
      this.done();
    },
    done() {
      if (this.$account.walletsNeedSynchronization.length) {
        this.next('synchronization');
      } else if (this.$account.cryptosToSelect) {
        this.next('selectCryptos');
      } else {
        this.redirectToApp();
      }
    },
  },
};
</script>

<template>
  <AuthStepLayout
    class="&"
    :title="$t('Enter your PIN')"
    :showBack="false"
    :centered="true" :description="$t('for quick access')"
  >
    <div class="&__pin">
      <MwPin
        v-if="isReady"
        ref="pin"
        mode="deviceSeed"
        logoutButton
        @success="success"
      />
    </div>
    <details class="&__help"><summary>{{ $t('Forgot your PIN?') }}</summary><p>{{ $t('Your 12 words restore your wallet. You can also save this optional recovery file. It contains no private keys.') }}</p><p>{{ $t('Log out only if you have your recovery phrase.') }}</p></details>
  </AuthStepLayout>
</template>

<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app
 * pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } {
  .auth-step-layout__content { justify-content: safe center; }
  &__pin { flex: none; display: flex; flex-direction: column; gap: var(--space-lg); }
  &__help { width: min(100%, 22rem); margin-inline: auto; font-size: var(--text-label); color: var(--color-muted); }
  &__help summary { min-height: 2.75rem; padding-block: var(--space-sm); cursor: pointer; color: var(--color-accent); text-align: center; }
  &__help p { line-height: 1.6; }
}
</style>
