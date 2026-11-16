<script>
import { formatMidoriTag } from '../../lib/tags.js';
import { LEGAL_URLS } from '../../lib/legal.js';
import MwAvatar from '../../components/MwAvatar.vue';
import MwButton from '../../components/MwButton.vue';
import MwModal from '../../components/MwModal.vue';
import MwListItem from '../../components/MwListItem.vue';
import MwListItemDropdown from '../../components/MwListItemDropdown.vue';
import MainLayout from '../../layouts/MainLayout.vue';
import { currencies } from '../../lib/account/Market.js';
import { prettyVersion } from '../../lib/version.js';
import { languages } from '../../lib/i18n/i18n.js';
export default {
 components: { MainLayout, MwAvatar, MwButton, MwModal, MwListItem, MwListItemDropdown },
 data() { return { version: prettyVersion, currency: this.$currency, currencies: currencies.map(value => ({ value, name: value })), language: this.$i18n.locale, languages, theme: this.$account.theme, saving: false, error: '', saved: false, confirmLogout: false, active: true, legal: LEGAL_URLS }; },
 computed: {
  tag() { return formatMidoriTag(this.$account.tag?.alias); },
  themes() { return ['System', 'Light', 'Dark'].map(name => ({ name: this.$t(name), value: name.toLowerCase() })); },
  dirty() { return this.currency !== this.$currency || this.language !== this.$i18n.locale || this.theme !== this.$account.theme; },
 },
 beforeUnmount() { this.active = false; },
 methods: {
  cancelPreferences() { this.currency = this.$currency; this.language = this.$i18n.locale; this.theme = this.$account.theme; this.error = ''; this.saved = false; },
  async savePreferences() {
   if (this.saving || !this.dirty) return; this.saving = true; this.error = ''; this.saved = false;
   const details = this.$account.details, previous = { ...details.get('systemInfo') };
   try { details.set('systemInfo', { ...previous, preferredCurrency: this.currency, language: this.language, theme: this.theme }); await details.save(); if (!this.active || this.$account.isLocked) return; for (const key of ['currency', 'language', 'theme']) this.$account.emit('update', key); this.$account.emit('update'); this.saved = true; }
   catch { details.set('systemInfo', previous); if (this.active) this.error = this.$t('Could not save changes. Try again.'); }
   finally { this.saving = false; }
  },
  logout() { this.$account.logout(); this.$router.replace({ name: 'auth' }); },
 },
};
</script>
<template><MainLayout class="&" :title="$t('Account')" wide><div class="&__intro"><MwAvatar :avatar="$user.avatar" own :size="56" :alt="$t('Account')" /><div><h1>{{ tag || $t('Your wallet') }}</h1><p>{{ $t('Your keys. Your control.') }}</p></div></div><div class="&__sections">
<section aria-labelledby="account-profile"><h2 id="account-profile">{{ $t('Profile') }}</h2><ul><MwListItem :title="$t('Your Midori tag')" :description="tag || $t('Choose a Midori tag to receive money more easily.')" @click="$router.push({name:'settings.account'})" /></ul></section>
<section aria-labelledby="account-preferences"><h2 id="account-preferences">{{ $t('Preferences') }}</h2><fieldset :disabled="saving" :aria-label="$t('Preferences')"><ul><MwListItem :title="$t('Local currency')" :arrow="false"><template #after><MwListItemDropdown v-model="currency" :options="currencies" :aria-label="$t('Local currency')" /></template></MwListItem><MwListItem :title="$t('Language')" :arrow="false"><template #after><MwListItemDropdown v-model="language" :options="languages" :aria-label="$t('Language')" /></template></MwListItem><MwListItem :title="$t('Theme')" :arrow="false"><template #after><MwListItemDropdown v-model="theme" :options="themes" :aria-label="$t('Theme')" /></template></MwListItem></ul></fieldset><div class="&__actions"><MwButton type="primary" small :disabled="!dirty" :isLoading="saving" @click="savePreferences">{{ $t('Save') }}</MwButton><MwButton type="primary-link" small :disabled="saving || !dirty" @click="cancelPreferences">{{ $t('Cancel') }}</MwButton></div><p v-if="saved && !dirty" role="status">{{ $t('Changes saved') }}</p><p v-if="error" role="alert">{{ error }}</p></section>
<section aria-labelledby="account-security"><h2 id="account-security">{{ $t('Security') }}</h2><ul><MwListItem :title="$t('PIN')" @click="$router.push({name:'settings.pin'})" /><MwListItem :title="$t('Recovery')" :description="$t('Recovery file') + ' · ' + $t('(optional)')" @click="$router.push({name:'settings.recovery'})" /><MwListItem :title="$t('Advanced tools')" @click="$router.push({name:'settings.advanced'})" /><MwListItem v-if="env.VITE_BUILD_TYPE !== 'web' && env.VITE_SITE_URL_TOR" :title="$t('Tor')" @click="$router.push({name:'settings.tor'})" /></ul></section>
<section aria-labelledby="account-connections"><h2 id="account-connections">{{ $t('Connections') }}</h2><ul><MwListItem :title="$t('WalletConnect')" :description="$t('Connecting does not send money or sign a message.')" @click="$router.push({name:'settings.walletconnect'})" /></ul></section>
<section aria-labelledby="account-help"><h2 id="account-help">{{ $t('Help') }}</h2><ul><MwListItem :title="$t('Help')" :description="$t('Recovery file') + ' · ' + $t('PIN') + ' · ' + $t('Activity')" @click="$router.push({name:'settings.help'})" /></ul></section>
<section aria-labelledby="account-about"><h2 id="account-about">{{ $t('About') }}</h2><ul><MwListItem :title="$t('Terms of Service')" @click="$safeOpen(legal.terms)" /><MwListItem :title="$t('Privacy Policy')" @click="$safeOpen(legal.privacy)" /></ul><p class="&__version">Midori Wallet · {{ version }}</p></section>
</div><MwButton type="primary-link" @click="confirmLogout = true">{{ $t('Log out') }}</MwButton><MwModal :show="confirmLogout" :title="$t('Log out')" @close="confirmLogout = false"><p>{{ $t('Log out only if you have your recovery phrase.') }}</p><p>{{ $t('Logging out removes wallet access from this device. It does not move funds or delete transaction records.') }}</p><template #footer><MwButton type="danger-light" @click="logout">{{ $t('Log out') }}</MwButton><MwButton type="secondary" @click="confirmLogout = false">{{ $t('Cancel') }}</MwButton></template></MwModal></MainLayout></template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · pre-emit critique: P5 H5 E4 S5 R5 V4 */
.#{ $filename } { &__intro { display: flex; gap: var(--space-md); align-items: center; } h1 { font-size: 1.5rem; margin: 0; overflow-wrap: anywhere; } p { color: var(--color-muted); font-size: var(--text-label); line-height: 1.7; } &__sections { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-xl); } section { min-width: 0; } h2 { font-size: 1rem; margin: 0; padding-block: var(--space-sm); border-bottom: 1px solid var(--color-rule); } ul { padding: 0; margin: 0; list-style: none; } fieldset { padding: 0; margin: 0; border: 0; min-width: 0; } &__actions { display: flex; flex-wrap: wrap; gap: var(--space-xs); padding-top: var(--space-sm); } @media(min-width: 60rem) { &__sections { grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: var(--space-2xl); } } }
</style>
