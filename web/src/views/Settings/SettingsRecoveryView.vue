<script>
import MainLayout from '../../layouts/MainLayout.vue';
import MwButton from '../../components/MwButton.vue';
import MwButtonGroup from '../../components/MwButtonGroup.vue';
import MwWarning from '../../components/MwWarning.vue';
import { saveRecoveryFile, nativeRecoveryFiles } from '../../lib/storage/RecoveryFiles.js';
import { verifyDescriptor } from '../../lib/account/recovery.js';

export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwWarning },
  data() { return { verified: false, error: '' }; },
  methods: {
    async download() {
      try { await saveRecoveryFile(this.$account.recoveryDescriptor); this.error = ''; }
      catch { this.error = this.$t('Recovery file could not be opened or saved.'); }
    },
    async chooseFile() {
      const native = nativeRecoveryFiles();
      if (!native) return this.$refs.file.click();
      const descriptor = this.$account.recoveryDescriptor;
      this.verified = false; this.error = '';
      try { verifyDescriptor(descriptor, await native.read()); this.verified = true; }
      catch { this.error = this.$t('The recovery file does not match this account.'); }
    },
    async verify(event) {
      this.verified = false; this.error = '';
      try {
        const file = event.target.files[0];
        if (!file || file.size > 32768) throw new Error();
        verifyDescriptor(this.$account.recoveryDescriptor, await file.text());
        this.verified = true;
      } catch { this.error = this.$t('The recovery file does not match this account.'); }
      event.target.value = '';
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Recovery file')">
    <div>{{ $t('Your 12 words restore your wallet. You can also save this optional recovery file. It contains no private keys.') }}</div>
    <MwButtonGroup>
      <MwButton type="primary" @click="download">{{ $t('Save recovery file') }}</MwButton>
      <MwButton type="secondary" @click="chooseFile">{{ $t('Verify saved recovery file') }}</MwButton>
    </MwButtonGroup>
    <input ref="file" type="file" accept="application/json,.json" hidden style="display: none" @change="verify">
    <div v-if="verified" role="status">{{ $t('Recovery file verified') }}</div>
    <MwWarning v-if="error" role="alert">{{ error }}</MwWarning>
  </MainLayout>
</template>
