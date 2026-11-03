<script>
import MwButton from '../../components/MwButton.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../components/MwForm/MwFormInput.vue';
import MwStep from '../../components/MwStep.vue';
import MainLayout from '../../layouts/MainLayout.vue';
import { isValidEmail } from '../../lib/helpers.js';
import { onShowOnHide } from '../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormGroup,
    MwFormInput,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  async onShow() {
    if (this.args?.errors) {
      this.errors = this.args?.errors;
    }
  },
  data() {
    return {
      isLoading: false,
      errors: {},
      email: this.storage.kyc.email,
      phone: `+${this.storage.kyc.phoneAreaCode || ''} ${this.storage.kyc.phoneNumber || ''}`.trim(),
    };
  },
  methods: {
    async confirm() {
      this.isLoading = true;
      try {
        if (this.errors.email) return;
        if (!isValidEmail(this.email)) {
          return this.errors.email = this.$t('Invalid email');
        }

        const parsePhoneNumber = (await import('libphonenumber-js')).default;
        const phoneNumber = parsePhoneNumber(this.phone);
        if (!phoneNumber) {
          return this.errors.phone = this.$t('Invalid value');
        }
        this.updateStorage({
          kyc: {
            ...this.storage.kyc,
            email: this.email,
            phoneNumber: phoneNumber.nationalNumber,
            phoneAreaCode: phoneNumber.countryCallingCode,
          },
        });
        const countryCode = await this.$account.cards.getCountryCode();
        this.next('kycAddress', { countryCode });
      } catch (err) {
        console.error(err);
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('KYC')"
    :description="$t('Contact information')"
  >
    <MwFormGroup class="&__content">
      <MwFormInput
        v-model="email"
        :label="$t('Email address')"
        :clear="true"
        inputmode="email"
        :error="errors.email"
        @update:modelValue="errors.email = undefined"
      />
      <MwFormInput
        v-model="phone"
        :label="$t('Phone number')"
        :clear="true"
        :error="errors.phone"
        inputmode="numeric"
        @update:modelValue="errors.phone = undefined"
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
