<script>
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../components/MwStep.vue';
import MainLayout from '../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwFormGroup,
    MwFormTextareaReadonly,
  },
  extends: MwStep,
};
</script>

<template>
  <MainLayout :title="$t('Card details')">
    <MwFormGroup>
      <MwFormTextareaReadonly
        :label="$t('Cardholder name')"
        :value="args.cardholderName"
        copy
      />
      <MwFormTextareaReadonly
        :label="$t('Card number')"
        :value="args.cardNumber.match(/.{1,4}/g).join(' ')"
        :copyValue="args.cardNumber"
        copy
      />
      <MwFormTextareaReadonly
        :label="$t('Expiry date')"
        :value="args.expiryDate"
        copy
      />
      <MwFormTextareaReadonly
        :label="$t('Security code')"
        :value="args.securityCode"
        copy
      />
    </MwFormGroup>
  </MainLayout>
</template>
