<script>
import MwButton from '../../../components/MwButton.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import { walletSeed } from '../../../lib/mixins.js';

import DangerTriangleIcon from '../../../assets/svg/dangerTriangle.svg';

export default {
  components: {
    MainLayout,
    MwButton,
    DangerTriangleIcon,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
    };
  },
  methods: {
    async show() {
      if (this.$wallet.isAccountRecoverySupported) {
        this.next('pin', { layout: 'MainLayout', mode: 'export', success: data => { this.updateStorage({ data }); this.next('show'); } });
        return;
      }
      this.isLoading = true;
      await this.walletSeed(async (walletSeed) => {
        const data = await this.$wallet.getPrivateKey(walletSeed);
        this.updateStorage({ data });
        this.next('show');
      });
      this.isLoading = false;
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Export private keys')"
  >
    <div class="&__container">
      <div
        class="&__warning &__group"
      >
        <div class="&__warning-title">
          <DangerTriangleIcon class="&__danger_triangle" /> {{ $t('Warning!') }}
        </div>
        <div class="&__warning-info">
          <!-- eslint-disable-next-line max-len -->
          {{ $t("Your private keys are keys to your funds so DO NOT SHARE them with anyone unless you'd like them to have access to your funds.") }}
        </div>
      </div>
      <MwButton
        type="primary"
        :isLoading="isLoading"
        @click="show"
      >
        {{ $t('Show') }}
      </MwButton>
    </div>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      gap: var(--spacing-3xl);
    }

    &__group {
      &:last-of-type {
        flex-grow: 1;
      }
    }

    &__warning {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-md);
    }

    &__warning-title {
      @include text-md;
      @include text-bold;
      display: flex;
      align-items: flex-start;
      color: var(--color-danger);
      gap: var(--spacing-xs);
    }

    &__danger_triangle {
      width: var(--spacing-xl);
      flex-shrink: 0;
    }

    &__warning-info {
      @include text-md;
      color: var(--color-danger);
    }
  }
</style>
