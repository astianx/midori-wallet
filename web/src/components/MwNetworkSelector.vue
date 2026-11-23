<script>
import { STABLECOIN_NETWORKS } from '../../../config/stablecoin-networks.js';
import MwModal from './MwModal.vue';
import MwNetworkLogo from './MwNetworkLogo.vue';
import MwAppIcon from './MwAppIcon.vue';
export default {
  components: { MwModal, MwNetworkLogo, MwAppIcon },
  props: { symbol: { type: String, default: 'USDC' }, network: { type: String, default: '' }, destination: { type: String, default: 'crypto' }, compact: Boolean },
  data() { return { open: false, filter: this.symbol === 'USDC' ? 'USDC' : 'USDT', query: '', busy: false, error: '' }; },
  computed: {
    current() { return STABLECOIN_NETWORKS.find(network => network.id === this.network); },
    rows() {
      const query = this.query.trim().toLocaleLowerCase();
      return STABLECOIN_NETWORKS.filter(network => network.assets.some(asset => this.matches(asset)) && (!query || network.name.toLocaleLowerCase().includes(query) || network.id.includes(query)));
    },
    supportedCount() { return this.rows.filter(network => this.available(network)).length; },
  },
  methods: {
    matches(asset) { return this.filter === 'USDC' ? asset.symbol === 'USDC' : ['USDT', 'USDT0'].includes(asset.symbol); },
    available(network) { return network.enabled && (network.id === 'base' || this.$account.capabilities.evm?.includes(network.id)); },
    assets(network) { return network.assets.map(asset => asset.symbol).join(' · '); },
    show() { this.filter = this.symbol === 'USDC' ? 'USDC' : 'USDT'; this.query = ''; this.error = ''; this.open = true; },
    async choose(network) {
      if (!this.available(network) || this.busy) return;
      const asset = network.assets.find(asset => this.matches(asset));
      const id = `${asset.contract}@${network.id}`, crypto = this.$account.cryptoDB.get(id);
      if (!crypto) return;
      this.busy = true; this.error = '';
      try {
        if (!this.$account.wallet(id)) await this.$account.addWallet(crypto);
        this.open = false;
        await this.$router.push({ name: this.destination, params: { cryptoId: id }, ...(this.destination === 'crypto.receive' ? { query: { source: 'external' } } : {}) });
      } catch { this.error = this.$t('Could not select this network. Please try again.'); }
      finally { this.busy = false; }
    },
  },
};
</script>
<template>
  <div class="&" :class="{ '&--compact': compact }">
    <button type="button" class="&__trigger" aria-haspopup="dialog" :aria-expanded="open" @click="show">
      <MwNetworkLogo v-if="current && !compact" :network="current" />
      <span><small v-if="!compact">{{ $t('Network') }}</small><strong>{{ current ? current.name : $t('Stablecoin networks') }}</strong><small v-if="current && !compact">{{ $t('Accepts') }}: {{ assets(current) }}</small></span>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    <MwModal :show="open" :title="$t('Choose the network')" sheet @close="!busy && (open = false)">
      <div class="&__picker" :aria-busy="busy">
        <p class="&__intro">{{ $t('Choose the same network in the wallet or exchange you send from.') }}</p>
        <div class="&__tabs" role="group" :aria-label="$t('Crypto assets')"><button v-for="token in ['USDC', 'USDT']" :key="token" type="button" :aria-pressed="filter === token" :disabled="busy" @click="filter = token">{{ token }}</button></div>
        <p v-if="filter === 'USDT'" class="&__notice">{{ $t('Base accepts native USDC only. USDT0 is an omnichain representation of USDT.') }}</p>
        <label class="&__search"><span>{{ $t('Search networks') }}</span><input v-model="query" type="search" :placeholder="$t('Search networks')" :disabled="busy" /></label>
        <p class="&__count">{{ $t('{count} networks available in Midori', { count: supportedCount }) }}</p>
        <p v-if="error" role="alert" class="&__error">{{ error }}</p>
        <ul class="&__list">
          <li v-for="item in rows" :key="item.id">
            <button type="button" class="&__option" :disabled="busy || !available(item)" :aria-pressed="item.id === network && available(item) && filter === (symbol === 'USDC' ? 'USDC' : 'USDT')" @click="choose(item)">
              <MwNetworkLogo :network="item" /><span class="&__name"><strong>{{ item.name }}</strong><small>{{ assets(item) }}</small></span>
              <span class="&__availability"><MwAppIcon v-if="item.id === network && available(item) && filter === (symbol === 'USDC' ? 'USDC' : 'USDT')" name="check" /><small v-else>{{ $t(available(item) ? 'Available' : 'Not available in Midori') }}</small></span>
            </button>
          </li>
        </ul>
        <p v-if="!rows.length" role="status">{{ $t('No networks found.') }}</p>
        <p class="&__footnote">{{ $t('Unavailable networks cannot receive funds in Midori.') }}</p>
      </div>
    </MwModal>
  </div>
</template>
<style lang="scss">
.#{ $filename } {
  &__trigger { display: flex; align-items: center; width: 100%; min-height: 4.5rem; padding: var(--spacing-md); gap: var(--spacing-md); border: 1px solid var(--color-divider); border-radius: var(--radius-control); text-align: start; }
  &__trigger > span:not(.mw-network-logo) { flex: 1; min-width: 0; }
  &__trigger strong, &__trigger small { display: block; }
  &__trigger strong { font-size: 1rem; font-weight: 600; }
  &__trigger small { font-size: 0.75rem; color: var(--color-secondary); line-height: 1.5; }
  &__trigger > svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__trigger:hover { background: var(--color-secondary-light); }
  &--compact &__trigger { min-height: 2.75rem; padding: var(--spacing-xs) var(--spacing-sm); border-color: transparent; color: var(--color-primary); }
  &--compact &__trigger strong { font-size: 0.875rem; font-weight: 500; }
  &__picker { display: flex; flex-direction: column; gap: var(--spacing-md); min-width: 0; }
  &__intro, &__notice, &__footnote, &__count { margin: 0; color: var(--color-secondary); font-size: 0.8125rem; line-height: 1.5; }
  &__notice { padding: var(--spacing-sm); border-radius: var(--radius-control); background: var(--color-warning-surface); color: var(--color-warning); }
  &__tabs { display: grid; grid-template-columns: 1fr 1fr; gap: var(--spacing-xs); padding: var(--spacing-2xs); background: var(--color-secondary-light); border-radius: var(--radius-control); }
  &__tabs button { min-height: 2.75rem; border-radius: calc(var(--radius-control) - var(--spacing-2xs)); font-size: 0.875rem; font-weight: 600; }
  &__tabs button[aria-pressed='true'] { background: var(--color-background); color: var(--color-primary); }
  &__search span { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
  &__search input { width: 100%; min-height: 2.75rem; padding: var(--spacing-sm) var(--spacing-md); border: 1px solid var(--color-divider); border-radius: var(--radius-control); background: var(--color-background); color: var(--color-text); font-size: 1rem; }
  &__list { list-style: none; padding: 0; margin: 0; max-height: min(36dvh, 24rem); overflow-y: auto; overscroll-behavior: contain; }
  &__list li + li { border-top: 1px solid var(--color-divider); }
  &__option { display: flex; width: 100%; min-height: 4.5rem; align-items: center; gap: var(--spacing-md); padding-block: var(--spacing-md); text-align: start; }
  &__option:hover:enabled { background: var(--color-secondary-light); }
  &__option:disabled { cursor: not-allowed; }
  &__name { flex: 1; min-width: 0; }
  &__name strong, &__name small { display: block; }
  &__name strong { font-weight: 500; font-size: 1rem; }
  &__name small { color: var(--color-secondary); font-size: 0.8125rem; margin-top: var(--spacing-2xs); }
  &__availability { max-width: 7rem; text-align: end; color: var(--color-secondary); line-height: 1.4; }
  &__availability small { font-size: 0.75rem; }
  &__availability svg { width: 1.5rem; height: 1.5rem; color: var(--color-primary); }
  &__error { color: var(--color-danger); margin: 0; }
}
</style>
