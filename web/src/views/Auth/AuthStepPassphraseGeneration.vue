<script>
import { beginRecovery } from '../../lib/account/recovery.js';

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
  methods: {
    async generate() {
      const recoveryHandle = await beginRecovery();
      this.updateStorage({ recoveryHandle });
      this.next('passphrase');
    },
  },
};
</script>

<template>
  <AuthStepLayout
    :title="$t('New wallet')"
    faded
  >
    <div class="&__wallet">
      <WalletGradientIcon class="&__wallet-icon" />
    </div>
    <div class="&__container">
      <div class="&__header">
        {{ $t('We are about to generate your very own passphrase') }}
      </div>
      <div class="&__text">
        {{ $t('This allows you to open your wallet on multiple devices and keeps it secure.') }}
      </div>
      <div class="&__text">
        {{ $t('It is very important to write down the passphrase.') }}
      </div>
    </div>
    <MwButton
      class="&__generate"
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
