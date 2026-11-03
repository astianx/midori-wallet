<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwPassphrase from '../../components/MwPassphrase.vue';
import MwStep from '../../components/MwStep.vue';

export default {
  components: {
    AuthStepLayout,
    MwPassphrase,
  },
  extends: MwStep,
  data() {
    return {
      passphrase: '',
    };
  },
  methods: {
    confirm(recoveryHandle) {
      this.updateStorage({ recoveryHandle });
      this.passphrase = '';
      this.next('pin');
    },
  },
};
</script>

<template>
  <AuthStepLayout
    :title="$t('Enter passphrase')"
    faded
  >
    <MwPassphrase
      v-model="passphrase"
      recovery
      @confirm="confirm"
    />
  </AuthStepLayout>
</template>
