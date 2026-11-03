<script>
import MwButton from '../../components/MwButton.vue';
import MwFormFilepicker from '../../components/MwForm/MwFormFilepicker.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwStep from '../../components/MwStep.vue';
import MainLayout from '../../layouts/MainLayout.vue';

import { walletSeed } from '../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormGroup,
    MwFormFilepicker,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
      errors: {},
      selfie: this.storage.kyc.selfie,
      fileMaxSize: 1 * 1024 * 1024,
      photoMinEdge: 300,
    };
  },
  methods: {
    async confirm() {
      if (!this.selfie) {
        return this.errors.selfie = this.$t('Invalid photo');
      } else if (this.selfie.photoMinEdge < this.photoMinEdge) {
        return this.errors.selfie = this.$t('Photo is too small. Minimum {size}px side.', { size: '300' });
      } else if (this.selfie.size > this.fileMaxSize) {
        return this.errors.selfie = this.$t('Photo is too large. Maximum {size} MB.', { size: '1' });
      }

      this.updateStorage({
        cardAction: 'kyc',
        kyc: {
          ...this.storage.kyc,
          selfie: this.selfie,
        },
      });
      await this.$nextTick();

      this.isLoading = true;
      await this.walletSeed(async () => {
        try {
          await this.$account.cards.submitKyc(this.storage.kyc);
          this.updateStorage({ status: true });
        } catch (err) {
          if (err?.message === 'Email already exist') {
            return this.backTo('kycContact', { errors: { email: this.$t('Email address is already in use') } });
          }
          this.updateStorage({ status: false, error: err });
        }
        this.next('status');
      });
      this.isLoading = false;
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('KYC')"
    :description="$t('Additional verification')"
  >
    <MwFormGroup class="&__content">
      <MwFormFilepicker
        v-model="selfie"
        :label="$t('Selfie')"
        :placeholder="$t('Select photo...')"
        :fileMaxSize="fileMaxSize"
        :photoMinEdge="photoMinEdge"
        :error="errors.selfie"
        :clear="true"
        capture="user"
        @update:modelValue="errors.selfie = undefined"
      />
    </MwFormGroup>

    <MwButton
      type="primary"
      :isLoading="isLoading"
      @click="confirm"
    >
      {{ $t('Continue') }}
    </MwButton>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__content {
      flex-grow: 1;
    }
  }
</style>
