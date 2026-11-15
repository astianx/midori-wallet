<script>
import { formatMidoriTag, isValidTagAlias } from '../../../lib/tags.js';
import { onShowOnHide } from '../../../lib/mixins.js';
import MwButton from '../../../components/MwButton.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
export default {
  components: { MainLayout, MwButton, MwFormInput, MwFormTextareaReadonly }, extends: MwStep, mixins: [onShowOnHide],
  data() { return { registeredTag: formatMidoriTag(this.$account.tag?.alias), locked: !!this.$account.tag, username: formatMidoriTag(this.$account.tag?.alias || this.$user.username), error: '', isLoading: false, online: false, allowed: false, active: false, generation: 0 }; },
  onShow() { this.active = true; this.generation++; this.sync(); this.$account.on('update', this.sync); window.addEventListener('online', this.sync); window.addEventListener('offline', this.sync); this.$nextTick(() => this.$refs.field?.$el.querySelector('input')?.focus()); },
  onHide() { this.active = false; this.generation++; this.$account.off('update', this.sync); window.removeEventListener('online', this.sync); window.removeEventListener('offline', this.sync); },
  methods: {
    sync() { this.online = navigator.onLine !== false && this.$account.connection === 'online'; this.allowed = this.$account.capabilities.tags === true; this.registeredTag = formatMidoriTag(this.$account.tag?.alias); this.locked ||= !!this.$account.tag; },
    async save() {
      if (this.isLoading || !this.active) return;
      this.sync();
      if (!this.online || !this.allowed || this.locked) return;
      if (!isValidTagAlias(this.username)) { this.error = this.$t('Invalid or reserved tag'); return; }
      const generation = this.generation; this.isLoading = true; this.error = '';
      try { await this.$account.updateUsername(this.username); this.$account.emit('update','user'); if (this.active && generation === this.generation) this.back({ saved: true }); }
      catch (error) { if (this.active && generation === this.generation) this.error = this.$t(error.code === 'TAG_IMMUTABLE' ? 'This tag is already registered and cannot be changed.' : error.code === 'TAG_TAKEN' || error.status === 409 ? 'Username already taken' : error.code === 'NETWORK_UNCONFIRMED' ? 'Connect to save your tag. Your Base address is still available.' : error.status === 400 ? 'Invalid or reserved tag' : 'Error! Please check your internet connection and try again.'); }
      finally { if (this.active && generation === this.generation) { this.sync(); this.$account.emit('update','user'); } if (generation === this.generation) this.isLoading = false; }
    },
  },
};
</script>
<template>
  <MainLayout class="&" :title="$t('Choose your tag')" allowReadFailure :onBack="() => back()">
    <p class="&__context">{{ $t('Receive {symbol}', { symbol: $wallet.crypto.symbol }) }} · Base</p><p>{{ $t('Your username is your Midori tag. Share it to receive USDT and USDC on Base.') }}</p>
    <p>{{ $t('Choose carefully. Your tag can only be registered once and cannot be changed.') }}</p>
    <MwFormTextareaReadonly v-if="locked" :value="registeredTag || '—'" :label="$t('Your Midori tag')" :copy="!!registeredTag" />
    <p v-if="locked && error" role="alert">{{ error }}</p>
    <form v-if="!locked" @submit.prevent="save"><MwFormInput ref="field" v-model="username" :label="$t('Your Midori tag')" placeholder="$usuario" :error="error" :disabled="isLoading" :clear="true" @update:modelValue="error = ''" /><p v-if="!online" role="status" class="&__notice">{{ $t('Connect to save your tag. Your Base address is still available.') }}</p><p v-else-if="!allowed" role="status" class="&__notice">{{ $t('Tags are unavailable. Use your Base address.') }}</p><MwButton class="&__save" type="primary" :isLoading="isLoading" :disabled="!online || !allowed" @click="save">{{ $t('Save') }}</MwButton></form>
    <MwButton type="primary-link" @click="back">{{ $t('Receive {symbol}', { symbol: $wallet.crypto.symbol }) }}</MwButton>
  </MainLayout>
</template>
<style lang="scss">
/* Hallmark · design-system: design.md · pre-emit critique: P5 H4 E4 S5 R5 V4 */
.#{ $filename } { &__context { margin: 0; color: var(--color-accent); font-weight: 600; } p { font-size: var(--text-label); line-height: 1.5; } form { display: flex; flex-direction: column; gap: var(--space-lg); } &__save { width: 100%; } &__notice { color: var(--color-warning); } }
</style>
