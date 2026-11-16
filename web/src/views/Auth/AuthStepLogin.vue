<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwPassphrase from '../../components/MwPassphrase.vue';
import MwButton from '../../components/MwButton.vue';
import MwStep from '../../components/MwStep.vue';
import { nativeRecoveryFiles } from '../../lib/storage/RecoveryFiles.js';
import { onShowOnHide } from '../../lib/mixins.js';
export default { components: { AuthStepLayout, MwPassphrase, MwButton }, extends: MwStep, mixins: [onShowOnHide],
 data() { return { passphrase: '', descriptor: undefined, fileError: '' }; },
 onHide() { this.passphrase = ''; },
 methods: {
  confirm(recoveryHandle) { this.updateStorage({ recoveryHandle }); this.passphrase = ''; this.next('pin'); },
  parse(text) { const descriptor = JSON.parse(text); if (!descriptor || typeof descriptor !== 'object' || Array.isArray(descriptor)) throw Error(); this.descriptor = descriptor; this.fileError = ''; },
  async chooseFile() { const native = nativeRecoveryFiles(); if (!native) return this.$refs.file.click(); try { const text = await native.read(); if (text) this.parse(text); } catch (error) { if (error.name !== 'AbortError') this.fileError = this.$t('Recovery file could not be opened or saved.'); } },
  async readFile(event) { const file = event.target.files[0]; if (!file) return; try { if (file.size > 32768) throw Error(); this.parse(await file.text()); } catch { this.fileError = this.$t('The recovery file does not match this account.'); } finally { event.target.value = ''; } },
 },
};
</script>
<template><AuthStepLayout :title="$t('Open existing wallet')" :stepNumber="1" :stepCount="2"><p>{{ $t('Your 12 words restore your wallet. You can also save this optional recovery file. It contains no private keys.') }}</p><details><summary>{{ $t('Recovery file') }} · {{ $t('(optional)') }}</summary><input ref="file" type="file" accept="application/json,.json" hidden @change="readFile"><MwButton type="secondary" @click="chooseFile">{{ $t('Recovery file') }}</MwButton><p v-if="descriptor" role="status">{{ $t('Recovery file ready') }}</p><MwButton v-if="descriptor" type="primary-link" @click="descriptor = undefined">{{ $t('Remove') }}</MwButton><p v-if="fileError" role="alert">{{ fileError }}</p></details><MwPassphrase v-model="passphrase" recovery :recoveryDescriptor="descriptor" @confirm="confirm" /></AuthStepLayout></template>
