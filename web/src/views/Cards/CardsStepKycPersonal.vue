<script>
import MwButton from '../../components/MwButton.vue';
import MwFormDatepicker from '../../components/MwForm/MwFormDatepicker.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../components/MwForm/MwFormInput.vue';
import MwStep from '../../components/MwStep.vue';
import MainLayout from '../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormDatepicker,
    MwFormGroup,
    MwFormInput,
  },
  extends: MwStep,
  data() {
    return {
      errors: {},
      firstName: this.storage.kyc.firstName,
      lastName: this.storage.kyc.lastName,
      dateOfBirth: this.storage.kyc.dateOfBirth,
    };
  },
  methods: {
    confirm() {
      if (!this.firstName) {
        return this.errors.firstName = this.$t('Invalid value');
      }
      if (!this.lastName) {
        return this.errors.lastName = this.$t('Invalid value');
      }
      if (!this.dateOfBirth) {
        return this.errors.dateOfBirth = this.$t('Invalid value');
      }
      this.updateStorage({
        kyc: {
          ...this.storage.kyc,
          firstName: this.firstName,
          lastName: this.lastName,
          dateOfBirth: this.dateOfBirth,
        },
      });
      this.next('kycContact');
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('KYC')"
    :description="$t('Personal information')"
  >
    <MwFormGroup class="&__content">
      <MwFormInput
        v-model="firstName"
        :label="$t('First name')"
        :clear="true"
        :error="errors.firstName"
        @update:modelValue="errors.firstName = undefined"
      />
      <MwFormInput
        v-model="lastName"
        :label="$t('Last name')"
        :clear="true"
        :error="errors.lastName"
        @update:modelValue="errors.lastName = undefined"
      />
      <MwFormDatepicker
        v-model="dateOfBirth"
        :label="$t('Date of birth')"
        :error="errors.dateOfBirth"
        @update:modelValue="errors.dateOfBirth = undefined"
      />
    </MwFormGroup>

    <MwButton
      type="primary"
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
