<script>
import MwBalanceCard from '../components/MwBalanceCard.vue';
import MwCryptoLogo from '../components/MwCryptoLogo.vue';
import MwAppIcon from '../components/MwAppIcon.vue';
import MwActivityList from '../components/MwActivityList.vue';
import MwButton from '../components/MwButton.vue';
export default {
  components: { MwBalanceCard, MwCryptoLogo, MwAppIcon, MwActivityList, MwButton },
  data() { return { activity: { loading: true, partial: false, pending: false }, refreshingBalance: false }; },
  computed: {
    balanceFresh() { return this.$cryptos.length > 0 && this.$cryptos.every(item => item.readState === 'fresh' && item.balance?.value !== undefined); },
    emptyBalance() { return !this.$isHiddenBalance && this.balanceFresh && this.$cryptos.every(item => item.balance.value === 0n); },
    nextTitle() {
      if (this.activity.pending) return this.$t('Pending');
      if (this.activity.partial) return this.$t('Some activity could not be loaded.');
      if (!this.balanceFresh) return this.$t(this.$cryptos.some(item => item.balance) ? 'Last known balance' : 'Balance unavailable');
      return this.$t(this.emptyBalance ? 'Start with USDC and USDT.' : 'Receive with Midori Wallet');
    },
    nextDescription() {
      if (this.activity.pending) return this.$t('Check the status before starting another operation.');
      if (this.activity.partial) return this.$t('Your transfers and orders appear here.');
      if (!this.balanceFresh) return this.$t(this.$cryptos.some(item => item.balance) ? 'Balances could not be updated. Last known amounts are shown.' : 'Error! Please try again later.');
      if (this.emptyBalance || !this.$account.capabilities.tags || !this.$account.tag) return this.$t('USDT and USDC on Base share your account address.');
      return this.$t('In Midori Wallet, you can also receive with {tag}.', { tag: `@${this.$user.username}` });
    },
  },
  methods: {
    async refreshBalance() {
      if (this.refreshingBalance) return;
      this.refreshingBalance = true;
      try { await this.$account.refreshBalances(); }
      finally { this.refreshingBalance = false; }
    },
  },
};
</script>
<template>
  <div class="&">
    <header class="&__header"><div><span class="&__brand">Midori Wallet</span><h1>{{ $t('Your wallet') }}</h1></div><RouterLink :to="{ name: 'settings' }" class="&__account" :aria-label="$t('Account')"><MwAppIcon name="user" /></RouterLink></header>
    <div class="&__overview"><MwBalanceCard /><section class="&__next" :aria-label="$t('Next step')"><span class="&__next-icon"><MwAppIcon :name="activity.pending ? 'clock' : !balanceFresh || activity.partial ? 'refresh' : 'receive'" /></span><div class="&__next-copy"><h2>{{ nextTitle }}</h2><p>{{ nextDescription }}</p></div><MwButton v-if="activity.pending" type="primary-link" @click="$refs.activity.openPending()">{{ $t('Show') }}<MwAppIcon name="arrow" /></MwButton><MwButton v-else-if="activity.partial" type="primary-link" :isLoading="activity.loading" @click="$refs.activity.load(true, true)">{{ $t('Refresh') }}</MwButton><MwButton v-else-if="!balanceFresh" type="primary-link" :isLoading="refreshingBalance" @click="refreshBalance">{{ $t('Refresh') }}</MwButton><RouterLink v-else :to="{ name: 'receive' }" class="&__next-link">{{ $t('Receive') }}<MwAppIcon name="arrow" /></RouterLink></section></div>
    <section class="&__assets"><div class="&__section-heading"><h2>{{ $t('Stablecoins') }}</h2><span>Base</span></div><div class="&__asset-grid"><RouterLink v-for="item in $cryptos" :key="item.crypto._id" :to="{ name: 'crypto', params: { cryptoId: item.crypto._id } }" class="&__asset"><div class="&__asset-heading"><MwCryptoLogo :crypto="item.crypto" :platform="item.platform" /><span>{{ item.crypto.symbol }}<small>{{ item.crypto.name }}</small></span><MwAppIcon name="arrow" /></div><div class="&__asset-value" dir="ltr">{{ $isHiddenBalance ? '••••' : item.balance?.toString() ?? '—' }}<span>{{ item.crypto.symbol }}</span></div><span class="&__asset-state">{{ item.readState === 'fresh' ? 'Base' : $t(item.balance ? 'Last known balance' : 'Balance unavailable') }}</span></RouterLink></div></section>
    <section class="&__activity"><div class="&__section-heading"><h2>{{ $t('Activity') }}</h2><RouterLink :to="{ name: 'activity' }">{{ $t('Show') }}<MwAppIcon name="arrow" /></RouterLink></div><MwActivityList ref="activity" compact @summary="activity = $event" /></section>
  </div>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app · pre-emit critique: P4 H5 E5 S5 R5 V4 */
.#{ $filename } {
  height: 100%; overflow-y: auto; overscroll-behavior: contain; padding: var(--space-lg); color: var(--color-ink);
  &__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-lg); gap: var(--space-md); }
  &__brand { display: block; color: var(--color-muted); font-size: var(--text-label); margin-bottom: var(--space-2xs); }
  h1 { margin: 0; font-size: var(--text-title); line-height: 1.2; font-weight: 600; letter-spacing: -0.04em; }
  h2 { margin: 0; font-size: 1rem; font-weight: 600; letter-spacing: -0.025em; }
  &__account { display: grid; place-items: center; width: 2.75rem; height: 2.75rem; border: 1px solid var(--color-rule); border-radius: 50%; color: var(--color-ink); background: var(--color-surface); }
  &__account svg { width: 1.125rem; height: 1.125rem; }
  &__overview { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-lg); }
  &__next { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm) var(--space-md); padding-block: var(--space-md); border-bottom: 1px solid var(--color-rule); }
  &__next-icon { display: grid; place-items: center; flex: none; width: 2.75rem; height: 2.75rem; color: var(--color-accent); }
  &__next-icon svg { width: 1.5rem; height: 1.5rem; }
  &__next-copy { flex: 1; min-width: min(100%, 12rem); }
  &__next h2 { line-height: 1.35; overflow-wrap: anywhere; }
  &__next p { margin-block: var(--space-xs) 0; color: var(--color-muted); font-size: 0.875rem; line-height: 1.5; }
  &__next-link { display: flex; align-items: center; gap: var(--space-xs); min-height: 2.75rem; font-size: 0.875rem; font-weight: 550; white-space: nowrap; }
  &__next-link svg { width: 1.125rem; height: 1.125rem; flex: none; }
  &__assets, &__activity { margin-top: var(--space-xl); }
  &__section-heading { display: flex; justify-content: space-between; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-md); }
  &__section-heading > span { font-size: var(--text-label); color: var(--color-muted); }
  &__section-heading > a { display: flex; gap: var(--space-xs); align-items: center; font-size: var(--text-label); font-weight: 550; white-space: nowrap; }
  &__section-heading > a svg { width: 1rem; height: 1rem; }
  &__asset-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-sm); }
  &__asset { display: block; min-width: 0; border: 1px solid var(--color-rule); border-radius: var(--radius-panel); padding: var(--space-lg); background: var(--color-surface); color: var(--color-ink); transition: transform var(--dur-short) var(--ease-out); }
  &__asset:hover { border-color: var(--color-accent); color: var(--color-ink); }
  &__asset:active { transform: translateY(1px); }
  &__asset-heading { display: flex; gap: var(--space-sm); align-items: center; font-weight: 600; font-size: 0.9375rem; }
  &__asset-heading > .mw-crypto-logo { width: 2.5rem; height: 2.5rem; flex: none; }
  &__asset-heading > span { flex: 1; }
  &__asset-heading small { display: block; margin-top: var(--space-2xs); font-size: 0.75rem; font-weight: 400; color: var(--color-muted); }
  &__asset-heading > svg:last-child { width: 1.125rem; height: 1.125rem; color: var(--color-muted); }
  &__asset-value { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-lg); font-size: 1.5rem; font-weight: 550; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; letter-spacing: -0.035em; }
  &__asset-value > span { font-size: 0.75rem; font-weight: 400; color: var(--color-muted); letter-spacing: 0; }
  &__asset-state { font-size: 0.6875rem; color: var(--color-muted); }
  @media (max-width: 47.99rem) {
    &__asset { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 0.85fr); gap: var(--space-xs) var(--space-sm); padding: var(--space-md); }
    &__asset-heading > svg:last-child { display: none; }
    &__asset-value { margin: 0; justify-content: flex-end; align-self: center; font-size: 1.125rem; text-align: end; }
    &__asset-state { grid-column: 1 / -1; font-size: 0.75rem; }
    &__assets, &__activity { margin-top: var(--space-lg); }
  }
  @include breakpoint(sm) { &__asset-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  @include breakpoint(lg) { max-width: 76rem; margin-inline: auto; padding: var(--space-lg) var(--space-xl) var(--space-2xl); &__overview { grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); } &__next { flex-direction: column; align-items: flex-start; justify-content: center; padding: var(--space-lg) var(--space-xs); border-bottom: 0; } &__next-copy { flex: none; } &__header { margin-bottom: var(--space-xl); } }
}
</style>
