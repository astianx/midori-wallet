<script>
import { groupStablecoins, stablecoinFamily } from '../lib/account/stablecoin-accounts.js';
import MainLayout from '../layouts/MainLayout.vue';
import MwAppIcon from '../components/MwAppIcon.vue';
import MwCryptoLogo from '../components/MwCryptoLogo.vue';
import MwButton from '../components/MwButton.vue';
import MwLoader from '../components/MwLoader.vue';
import { rampAssetSupported } from '../lib/account/ramp-presentation.js';
import { rampError } from '../lib/account/ramp-errors.js';

export default {
  components: { MainLayout, MwAppIcon, MwCryptoLogo, MwButton, MwLoader },
  props: { direction: { type: String, required: true, validator: value => ['add', 'withdraw'].includes(value) } },
  data() { return { options: null, loading: false, error: '', disposed: false }; },
  computed: {
    groupedAssets() { return groupStablecoins(this.$cryptos); },
    sendAssets() { return this.groupedAssets.filter(item => stablecoinFamily(item.crypto) === 'USDC' || ['bitcoin','spark'].includes(item.crypto.platform)).map(item => { const position = item.positions?.find(position => position.crypto.platform !== 'base' && this.$account.capabilities.evm?.includes(position.crypto.platform)) || item; return { ...position, title: item.title, subtitle: item.subtitle }; }).filter(item => item.crypto.platform !== 'base' || this.$account.capabilities.payments); },
    adding() { return this.direction === 'add'; },
    title() { return this.$t(this.adding ? 'Add money' : 'Withdraw'); },
    methods() {
      return this.adding
        ? [{ id: 'receive', label: this.$t('From another wallet'), description: this.$t('Crypto assets'), icon: 'receive', enabled: this.$cryptos.length > 0 }, { id: 'buy', label: this.$t('Local currency'), description: this.$t('Buy with local currency'), icon: 'plus', enabled: this.canUseRamp('buy') }]
        : [{ id: 'send', label: this.$t('To another wallet'), description: this.$t('Crypto assets'), icon: 'send', enabled: this.sendAssets.length > 0 }, { id: 'sell', label: this.$t('Local currency'), description: this.$t('Convert to local currency'), icon: 'refresh', enabled: this.canUseRamp('sell') }];
    },
    selected() { return this.methods.find(method => method.id === this.$route.query.method); },
    assetNetwork() { return this.isRamp && this.options?.environment === 'STAGING' ? this.$t('Test mode. Do not use real money.') : this.$t('Crypto assets'); },
    isRamp() { return ['buy', 'sell'].includes(this.selected?.id); },
    assets() {
      if (!this.selected?.enabled) return [];
      if (!this.isRamp) return this.selected.id === 'send' ? this.sendAssets : this.groupedAssets;
      const kind = this.selected.id;
      if (this.$account.capabilities.rampProvider === 'changelly') return this.$cryptos;
      if (!this.options?.products.includes(kind.toUpperCase()) || !this.options.fiatCurrencies.some(item => item[kind])) return [];
      return this.$cryptos.filter(item => this.options.assets.some(asset => asset.symbol === item.crypto.symbol && rampAssetSupported(this.options, asset, kind)));
    },
  },
  mounted() { if (this.$account.capabilities.ramp) this.loadOptions(); },
  beforeUnmount() { this.disposed = true; },
  methods: {
    canUseRamp(kind) {
      if (this.$account.capabilities.rampProvider === 'changelly') return !!this.$account.capabilities.ramp;
      return !!this.$account.capabilities.ramp && !!this.options?.products.includes(kind.toUpperCase())
        && this.options.fiatCurrencies.some(item => item[kind])
        && this.$cryptos.some(item => this.options.assets.some(asset => asset.symbol === item.crypto.symbol && rampAssetSupported(this.options, asset, kind)));
    },
    limitation(method) {
      if (method.id === 'send') return this.$t('Sending is temporarily unavailable. Your funds remain in your wallet.');
      if (method.id === 'receive') return this.$t('Balance unavailable');
      if (!this.$account.capabilities.ramp) return this.$t('The payment service is unavailable. Try again shortly.');
      if (this.error) return this.error;
      return this.$t(this.loading || !this.options ? 'Loading' : 'Unavailable');
    },
    choose(method) { if (method.enabled) this.$router.push({ name: this.$route.name, query: { ...this.$route.query, method: method.id } }); },
    back() {
      if (!this.selected) { this.$router.up(); return; }
      const previous = this.$router.options.history.state.back;
      if (previous && this.$router.resolve(previous).name === this.$route.name && !this.$router.resolve(previous).query.method) this.$router.back();
      else this.$router.replace({ name: this.$route.name, query: { ...this.$route.query, method: undefined } });
    },
    async loadOptions() {
      if (this.$account.capabilities.rampProvider === 'changelly') return;
      if (this.loading || !this.$account.capabilities.ramp || !this.$cryptos.length) return;
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
      <header class="&__intro"><span class="&__intro-icon"><MwAppIcon :name="selected?.icon || (adding ? 'plus' : 'send')" /></span><h1>{{ selected?.label || title }}</h1><p>{{ selected ? assetNetwork : $t('Choose a method') }}</p></header>
      <section class="&__choices" :aria-label="selected ? $t('Crypto assets') : $t('Choose a method')">
        <template v-if="!selected">
          <div v-for="method in methods" :key="method.id" class="&__method"><button type="button" class="&__row" :disabled="!method.enabled" :aria-describedby="`${direction}-${method.id}-description`" @click="choose(method)"><span class="&__icon"><MwAppIcon :name="method.icon" /></span><span class="&__label"><strong>{{ method.label }}</strong><small :id="`${direction}-${method.id}-description`">{{ method.description }}</small></span><MwAppIcon v-if="method.enabled" name="arrow" /></button><p v-if="!method.enabled" class="&__notice" role="status">{{ limitation(method) }}</p></div>
          <MwButton v-if="error" type="secondary" @click="loadOptions">{{ $t('Refresh') }}</MwButton>
        </template>
        <template v-else-if="!selected.enabled"><p class="&__notice" role="status">{{ limitation(selected) }}</p><MwButton v-if="error" type="secondary" @click="loadOptions">{{ $t('Refresh') }}</MwButton></template>
        <template v-else>
          <MwLoader v-if="loading" />
          <template v-else-if="error"><p class="&__notice" role="status">{{ error }}</p><MwButton type="secondary" @click="loadOptions">{{ $t('Refresh') }}</MwButton></template>
          <template v-else>
            <p v-if="isRamp && options?.environment === 'STAGING'" class="&__notice" role="status">{{ $t('Test mode. Do not use real money.') }}</p>
            <p v-if="!assets.length" class="&__notice" role="status">{{ $t(isRamp ? 'The selected asset or currency is unavailable.' : 'Balance unavailable') }}</p>
            <RouterLink v-for="item in assets" :key="item.crypto._id" class="&__row" :to="{ name: `crypto.${selected.id}`, params: { cryptoId: item.crypto._id } }"><MwCryptoLogo :crypto="item.crypto" :platform="item.platform" /><span class="&__label">{{ item.crypto.symbol }}<small>{{ item.subtitle || item.crypto.name }}{{ isRamp && options?.environment === 'STAGING' ? ' · Base Sepolia' : '' }}</small></span><MwAppIcon name="arrow" /></RouterLink>
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
  &__choices { min-width: 0; }
  &__method { border-bottom: 1px solid var(--color-rule); }
  &__row { display: flex; width: 100%; min-width: 0; align-items: center; min-height: 5.5rem; gap: var(--space-xs); padding: var(--space-md) var(--space-xs); text-align: start; color: var(--color-ink); }
  &__row:hover { background: var(--color-surface-subtle); color: var(--color-ink); }
  &__row:active { background: var(--color-surface-tint); }
  &__row:disabled { color: var(--color-muted); }
  &__label { flex: 1; min-width: 0; font-size: 0.875rem; font-weight: 550; overflow-wrap: anywhere; }
  &__label strong { display: block; font: inherit; white-space: normal; }
  &__label small { display: block; margin-top: var(--space-xs); color: var(--color-muted); font-size: var(--text-label); font-weight: 400; line-height: 1.5; }
  &__row > .mw-crypto-logo { width: 2.75rem; height: 2.75rem; flex: none; }
  &__row > svg:last-child, &__icon svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__choices > a { border-bottom: 1px solid var(--color-rule); }
  &__notice { margin-block: var(--space-xs) var(--space-md); color: var(--color-muted); font-size: var(--text-label); line-height: 1.5; }
  @media (min-width: 64rem) { &__composition { grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr); align-items: start; gap: var(--space-2xl); } }
}
</style>
