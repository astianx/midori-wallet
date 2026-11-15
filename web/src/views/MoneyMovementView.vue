<script>
import MainLayout from '../layouts/MainLayout.vue';
import MwAppIcon from '../components/MwAppIcon.vue';
import MwCryptoLogo from '../components/MwCryptoLogo.vue';
import MwButton from '../components/MwButton.vue';
import MwLoader from '../components/MwLoader.vue';
import { rampAssetSupported, rampNetwork } from '../lib/account/ramp-presentation.js';
import { rampError } from '../lib/account/ramp-errors.js';

export default {
  components: { MainLayout, MwAppIcon, MwCryptoLogo, MwButton, MwLoader },
  props: { direction: { type: String, required: true, validator: value => ['add', 'withdraw'].includes(value) } },
  data() { return { options: null, loading: false, error: '', disposed: false }; },
  computed: {
    adding() { return this.direction === 'add'; },
    title() { return this.$t(this.adding ? 'Add money' : 'Withdraw'); },
    methods() {
      return this.adding
        ? [{ id: 'receive', label: this.$t('From another wallet'), description: this.$t('USDT and USDC on Base share your account address.'), icon: 'receive', enabled: this.$cryptos.length > 0 }, { id: 'buy', label: this.$t('Local currency'), description: `Transak · ${this.$t('Buy with local currency')}`, icon: 'plus', enabled: !!this.$account.capabilities.ramp }]
        : [{ id: 'send', label: this.$t('To another wallet'), description: this.$t('Send and receive USDC and USDT with your own wallet.'), icon: 'send', enabled: !!this.$account.capabilities.payments && this.$cryptos.length > 0 }, { id: 'sell', label: this.$t('Local currency'), description: `Transak · ${this.$t('Convert to local currency')}`, icon: 'refresh', enabled: !!this.$account.capabilities.ramp }];
    },
    selected() { return this.methods.find(method => method.id === this.$route.query.method); },
    assetNetwork() { return this.isRamp ? rampNetwork(this.options?.environment) : 'Base'; },
    isRamp() { return ['buy', 'sell'].includes(this.selected?.id); },
    assets() {
      if (!this.selected?.enabled) return [];
      if (!this.isRamp) return this.$cryptos;
      const kind = this.selected.id;
      if (!this.options?.products.includes(kind.toUpperCase()) || !this.options.fiatCurrencies.some(item => item[kind])) return [];
      return this.$cryptos.filter(item => this.options.assets.some(asset => asset.symbol === item.crypto.symbol && rampAssetSupported(this.options, asset, kind)));
    },
  },
  mounted() { if (this.isRamp && this.selected.enabled) this.loadOptions(); },
  beforeUnmount() { this.disposed = true; },
  methods: {
    limitation(method) { return this.$t(method.id === 'send' ? 'Sending is temporarily unavailable. Your funds remain in your wallet.' : method.id === 'receive' ? 'Balance unavailable' : 'Buying or selling crypto is temporarily unavailable.'); },
    choose(method) { if (method.enabled) this.$router.push({ name: this.$route.name, query: { ...this.$route.query, method: method.id } }); },
    back() {
      if (!this.selected) { this.$router.up(); return; }
      const previous = this.$router.options.history.state.back;
      if (previous && this.$router.resolve(previous).name === this.$route.name && !this.$router.resolve(previous).query.method) this.$router.back();
      else this.$router.replace({ name: this.$route.name, query: { ...this.$route.query, method: undefined } });
    },
    async loadOptions() {
      if (this.loading || !this.isRamp || !this.selected.enabled || !this.$cryptos.length) return;
      this.loading = true; this.error = ''; this.options = null;
      try {
        const options = await this.$account.wallet(this.$cryptos[0].crypto._id).rampOptions();
        if (!this.disposed) this.options = options;
      } catch (error) { if (!this.disposed) this.error = rampError(error, this.$t); }
      finally { if (!this.disposed) this.loading = false; }
    },
  },
};
</script>
<template>
  <MainLayout class="&" :title="title" wide allowReadFailure :onBack="back">
    <div class="&__composition">
      <header class="&__intro"><span class="&__intro-icon"><MwAppIcon :name="selected?.icon || (adding ? 'plus' : 'send')" /></span><h1>{{ selected?.label || title }}</h1><p>{{ selected ? (isRamp ? assetNetwork : $t('Only Base is supported')) : $t('Choose a method') }}</p><span v-if="isRamp" class="&__provider">Transak</span></header>
      <section class="&__choices" :aria-label="selected ? $t('Stablecoins') : $t('Choose a method')">
        <template v-if="!selected">
          <div v-for="method in methods" :key="method.id" class="&__method"><button type="button" class="&__row" :disabled="!method.enabled" :aria-describedby="`${direction}-${method.id}-description`" @click="choose(method)"><span class="&__icon"><MwAppIcon :name="method.icon" /></span><span class="&__label"><strong>{{ method.label }}</strong><small :id="`${direction}-${method.id}-description`">{{ method.description }}</small></span><MwAppIcon v-if="method.enabled" name="arrow" /></button><p v-if="!method.enabled" class="&__notice" role="status">{{ limitation(method) }}</p></div>
        </template>
        <p v-else-if="!selected.enabled" class="&__notice" role="status">{{ limitation(selected) }}</p>
        <template v-else>
          <MwLoader v-if="loading" />
          <template v-else-if="error"><p class="&__notice" role="status">{{ error }}</p><MwButton type="secondary" @click="loadOptions">{{ $t('Refresh') }}</MwButton></template>
          <template v-else>
            <p v-if="isRamp && options?.environment === 'STAGING'" class="&__notice" role="status">{{ $t('Transak test mode. Use test funds only; orders do not change your Base mainnet balance.') }}</p>
            <p v-if="!assets.length" class="&__notice" role="status">{{ $t(isRamp ? 'This asset or currency is unavailable through Transak.' : 'Balance unavailable') }}</p>
            <RouterLink v-for="item in assets" :key="item.crypto._id" class="&__row" :to="{ name: `crypto.${selected.id}`, params: { cryptoId: item.crypto._id } }"><MwCryptoLogo :crypto="item.crypto" :platform="item.platform" /><span class="&__label">{{ item.crypto.symbol }}<small>{{ item.crypto.name }} · {{ assetNetwork }}</small></span><MwAppIcon name="arrow" /></RouterLink>
          </template>
        </template>
      </section>
    </div>
  </MainLayout>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } {
  &__composition { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-xl); }
  &__intro-icon, &__icon { display: grid; flex: none; place-items: center; width: 2.25rem; height: 2.75rem; color: var(--color-accent); }
  &__intro-icon svg { width: 1.75rem; height: 1.75rem; }
  &__intro h1 { margin-block: var(--space-md) var(--space-sm); font-size: var(--text-title); font-weight: 600; letter-spacing: -0.04em; line-height: 1.25; }
  &__intro p { margin: 0; color: var(--color-muted); font-size: 0.9375rem; line-height: 1.5; }
  &__provider { display: block; margin-top: var(--space-sm); color: var(--color-muted); font-size: var(--text-label); }
  &__choices { min-width: 0; }
  &__method { border-bottom: 1px solid var(--color-rule); }
  &__row { display: flex; width: 100%; min-width: 0; align-items: center; min-height: 5.5rem; gap: var(--space-xs); padding: var(--space-md) var(--space-xs); text-align: start; color: var(--color-ink); }
  &__row:hover { background: var(--color-surface-subtle); color: var(--color-ink); }
  &__row:active { background: var(--color-surface-tint); }
  &__row:disabled { color: var(--color-muted); }
  &__label { flex: 1; min-width: 0; font-size: 0.875rem; font-weight: 550; overflow-wrap: anywhere; }
  &__label strong { display: block; font: inherit; white-space: nowrap; }
  &__label small { display: block; margin-top: var(--space-xs); color: var(--color-muted); font-size: var(--text-label); font-weight: 400; line-height: 1.5; }
  &__row > .mw-crypto-logo { width: 2.75rem; height: 2.75rem; flex: none; }
  &__row > svg:last-child, &__icon svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__choices > a { border-bottom: 1px solid var(--color-rule); }
  &__notice { margin-block: var(--space-xs) var(--space-md); color: var(--color-muted); font-size: var(--text-label); line-height: 1.5; }
  @media (min-width: 64rem) { &__composition { grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr); align-items: start; gap: var(--space-2xl); } }
}
</style>
