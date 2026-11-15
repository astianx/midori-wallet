<script>
import MwButton from './MwButton.vue';
import MwModal from './MwModal.vue';
import MwAppIcon from './MwAppIcon.vue';
import MwCryptoLogo from './MwCryptoLogo.vue';
import MwLoader from './MwLoader.vue';
import { rampError } from '../lib/account/ramp-errors.js';
export default {
  components: { MwButton, MwModal, MwAppIcon, MwCryptoLogo, MwLoader },
  props: { action: { type: String, default: 'send' }, label: { type: String, required: true }, icon: { type: String, default: 'send' }, primary: { type: Boolean, default: false }, disabled: { type: Boolean, default: false } },
  data() { return { open: false, loading: false, options: null, error: '', request: 0 }; },
  computed: {
    isRamp() { return ['buy', 'sell'].includes(this.action); },
    allowed() { return !this.disabled && this.$cryptos.length > 0 && (this.action !== 'send' || !!this.$account.capabilities.payments) && (!this.isRamp || !!this.$account.capabilities.ramp); },
    assets() {
      if (!this.isRamp) return this.$cryptos;
      if (!this.options?.products.includes(this.action.toUpperCase()) || !this.options.fiatCurrencies.some(item => item[this.action])) return [];
      return this.$cryptos.filter(item => this.options.assets.some(asset => asset.symbol === item.crypto.symbol && asset[this.action]));
    },
  },
  beforeUnmount() { this.request++; },
  methods: {
    async show() {
      if (!this.allowed) return;
      this.open = true;
      if (!this.isRamp || this.loading) return;
      const request = ++this.request;
      this.loading = true; this.options = null; this.error = '';
      try {
        const options = await this.$account.wallet(this.$cryptos[0].crypto._id).rampOptions();
        if (request === this.request) this.options = options;
      } catch (error) { if (request === this.request) this.error = rampError(error, this.$t); }
      finally { if (request === this.request) this.loading = false; }
    },
    choose(id) {
      if (!this.allowed || !this.assets.some(item => item.crypto._id === id)) return;
      this.open = false;
      this.$router.push({ name: `crypto.${this.action}`, params: { cryptoId: id } });
    },
  },
};
</script>
<template>
  <MwButton :type="primary ? 'primary' : 'secondary'" :disabled="!allowed" @click="show"><MwAppIcon :name="icon" />{{ label }}</MwButton>
  <MwModal :show="open" :title="label" @close="open = false">
    <MwLoader v-if="loading" :aria-label="$t('Loading')" />
    <template v-else-if="error"><p role="status">{{ error }}</p><MwButton type="secondary" @click="show">{{ $t('Refresh') }}</MwButton></template>
    <template v-else>
      <p v-if="isRamp && options?.environment === 'STAGING'" class="&__notice" role="status">{{ $t('Test mode. Do not use real money.') }}</p>
      <p v-if="!assets.length" role="status">{{ $t('The selected asset or currency is unavailable.') }}</p>
      <button v-for="item in assets" :key="item.crypto._id" class="&__asset" type="button" @click="choose(item.crypto._id)"><MwCryptoLogo :crypto="item.crypto" :platform="item.platform" /><span>{{ item.crypto.symbol }}<small>Base</small></span><MwAppIcon name="arrow" /></button>
    </template>
  </MwModal>
</template>
<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P4 H5 E5 S5 R5 V4 */
.#{ $filename }__asset { display: flex; width: 100%; align-items: center; gap: var(--space-md); min-height: 4rem; padding: var(--space-sm); border-bottom: 1px solid var(--color-rule); text-align: start; color: var(--color-ink); }
.#{ $filename }__asset:hover { background: var(--color-surface-subtle); }
.#{ $filename }__asset:active { background: var(--color-surface-tint); }
.#{ $filename }__asset > .mw-crypto-logo { width: 2.5rem; height: 2.5rem; flex: none; }
.#{ $filename }__asset > span { flex: 1; font-weight: 600; }
.#{ $filename }__asset small { display: block; font-weight: 400; color: var(--color-muted); }
.#{ $filename }__asset > svg:last-child { width: 1.25rem; height: 1.25rem; flex: none; }
.#{ $filename }__notice { font-size: var(--text-label); color: var(--color-warning); }
</style>
