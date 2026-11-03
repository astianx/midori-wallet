<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwButton from '../../components/MwButton.vue';
import MwButtonGroup from '../../components/MwButtonGroup.vue';
import MwWarning from '../../components/MwWarning.vue';
import MwStep from '../../components/MwStep.vue';
import { saveRecoveryFile, nativeRecoveryFiles } from '../../lib/storage/RecoveryFiles.js';
import { recoveryDescriptor, verifyDescriptor } from '../../lib/account/recovery.js';
export default {
  components: { AuthStepLayout, MwButton, MwButtonGroup, MwWarning },
  extends: MwStep,
  data() { return { verified: false, error: '' }; },
  methods: {
    async download() {
      try { await saveRecoveryFile(recoveryDescriptor(this.storage.recoveryHandle)); }
      catch { this.error = this.$t('Recovery file could not be opened or saved.'); }
    },
    async chooseFile() {
      const native = nativeRecoveryFiles();
      if (!native) return this.$refs.file.click();
      try { verifyDescriptor(this.storage.recoveryHandle, await native.read()); this.verified = true; this.error = ''; }
      catch { this.verified = false; this.error = this.$t('The recovery file does not match this account.'); }
    },
    async verify(event) {
      this.verified = false; this.error = '';
      try {
        const file = event.target.files[0];
        if (!file || file.size > 32768) throw new Error();
        verifyDescriptor(this.storage.recoveryHandle, await file.text());
        this.verified = true;
      } catch { this.error = this.$t('The recovery file does not match this account.'); }
      event.target.value = '';
    },
  },
};
</script>
<template>
  <AuthStepLayout class="&" :title="$t('Save your recovery file')">
    <MwWarning>{{ $t('Keep this file with your 12 words. Both are required to recover your account. The file contains no private keys.') }}</MwWarning>
    <MwButtonGroup>
      <MwButton type="primary" @click="download">{{ $t('Save recovery file') }}</MwButton>
      <MwButton type="secondary" @click="chooseFile">{{ $t('Verify saved recovery file') }}</MwButton>
    </MwButtonGroup>
    <input ref="file" type="file" accept="application/json,.json" hidden @change="verify">
    <MwWarning v-if="error" role="alert">{{ error }}</MwWarning>
    <MwButton type="primary" :disabled="!verified" @click="next('pin')">{{ $t('Confirm') }}</MwButton>
  </AuthStepLayout>
</template>
