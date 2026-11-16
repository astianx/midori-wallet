<script>
import MainLayout from '../../layouts/MainLayout.vue';
import MwButton from '../../components/MwButton.vue';
import MwButtonGroup from '../../components/MwButtonGroup.vue';
import MwWarning from '../../components/MwWarning.vue';
import eventBus from '../../lib/eventBus.js';
import { saveRecoveryFile, nativeRecoveryFiles, publishRecoveryResult, takeRecoveryResult } from '../../lib/storage/RecoveryFiles.js';
import { verifyDescriptor } from '../../lib/account/recovery.js';

export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwWarning },
  data() { return { verified: false, error: '', busy: false }; },
  mounted() { eventBus.on('recoveryFileResult', this.syncResult); this.syncResult(); },
  beforeUnmount() { eventBus.off('recoveryFileResult', this.syncResult); },
  methods: {
    syncResult() { const result = takeRecoveryResult(this.$account.recoveryDescriptor?.address); if (result) { this.verified = result.verified; this.error = result.error; } },
    async download() {
      if (this.busy) return; this.busy = true;
      try { await saveRecoveryFile(this.$account.recoveryDescriptor); this.error = ''; }
      catch (error) { if (error.name !== 'AbortError') this.error = this.$t('Recovery file could not be opened or saved.'); } finally { this.busy = false; }
    },
    async chooseFile() {
      const native = nativeRecoveryFiles();
      if (!native) return this.$refs.file.click();
      const descriptor = this.$account.recoveryDescriptor;
      this.verified = false; this.error = '';
      if (this.busy) return; this.busy = true;
      try { const text = await native.read(); if (!text) return; verifyDescriptor(descriptor, text); publishRecoveryResult(descriptor.address, { verified: true, error: '' }); }
      catch (error) { if (error.name !== 'AbortError') publishRecoveryResult(descriptor.address, { verified: false, error: this.$t('The recovery file does not match this account.') }); } finally { this.busy = false; }
    },
    async verify(event) {
      this.verified = false; this.error = '';
      try {
        const file = event.target.files[0];
        if (!file) return;
        if (file.size > 32768) throw new Error();
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
      <MwButton type="primary" :isLoading="busy" @click="download">{{ $t('Save recovery file') }}</MwButton>
      <MwButton type="secondary" :disabled="busy" @click="chooseFile">{{ $t('Verify saved recovery file') }}</MwButton>
    </MwButtonGroup>
    <input ref="file" type="file" accept="application/json,.json" hidden style="display: none" @change="verify">
    <div v-if="verified" role="status">{{ $t('Recovery file verified') }}</div>
    <MwWarning v-if="error" role="alert">{{ error }}</MwWarning>
  </MainLayout>
</template>
