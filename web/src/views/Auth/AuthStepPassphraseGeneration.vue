<script>
import { onShowOnHide } from '../../lib/mixins.js';
import { beginRecovery, discardRecovery } from '../../lib/account/recovery.js';

import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwButton from '../../components/MwButton.vue';
import MwStep from '../../components/MwStep.vue';

import WalletGradientIcon from '../../assets/svg/walletGradient.svg';

export default {
  components: {
    MwButton,
    AuthStepLayout,
    WalletGradientIcon,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  onShow() { this.active = true; },
  onHide() { this.active = false; },
  data() { return { active: false, isLoading: false, error: '' }; },
  methods: {
    async generate() {
      if (this.isLoading) return; this.isLoading = true; this.error = '';
      try { const recoveryHandle = await beginRecovery(); if (!this.active) { discardRecovery(recoveryHandle); return; } this.updateStorage({ recoveryHandle }); this.next('passphrase'); }
      catch { this.error = this.$t('Error! Please check your internet connection and try again.'); }
      finally { this.isLoading = false; }
    },
  },
};
</script>

<template>
  <AuthStepLayout
    :title="$t('New wallet')" :stepNumber="1"
    faded
  >
    <div class="&__wallet">
      <WalletGradientIcon class="&__wallet-icon" />
    </div>
    <div class="&__container">
      <div class="&__header">
        {{ $t('12 words') }}
      </div>
      <div class="&__text">
        {{ $t('This allows you to open your wallet on multiple devices and keeps it secure.') }}
      </div>
      <div class="&__text">
        {{ $t('It is very important to write down the passphrase.') }}
      </div>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <MwButton
      class="&__generate" :isLoading="isLoading"
      type="primary"
      @click="generate"
    >
      {{ $t('Generate passphrase') }}
    </MwButton>
  </AuthStepLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__wallet {
      display: flex;
      flex-grow: 1;
      justify-content: center;
      @include breakpoint(lg) {
        flex-grow: 0;
        justify-content: flex-start;
      }
    }

    &__wallet-icon {
      width: var(--spacing-8xl);
      @include breakpoint(lg) {
        width: var(--spacing-4xl);
      }
    }

    &__container {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-md);
      @include breakpoint(lg) {
        flex-grow: 1;
      }
    }

    &__header {
      @include text-lg;
      @include text-bold;
    }

    &__text {
      @include text-md;
    }

    &__generate {
      flex-shrink: 0;
    }
  }
</style>
