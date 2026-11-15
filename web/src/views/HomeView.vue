<script>
import MwBalanceCard from '../components/MwBalanceCard.vue';
import MwCryptoLogo from '../components/MwCryptoLogo.vue';
import MwAppIcon from '../components/MwAppIcon.vue';
import MwAssetAction from '../components/MwAssetAction.vue';
import MwActivityList from '../components/MwActivityList.vue';
export default { components: { MwBalanceCard, MwCryptoLogo, MwAppIcon, MwAssetAction, MwActivityList } };
</script>
<template>
  <div class="&">
    <header class="&__header"><div><span class="&__brand">Midori Wallet</span><h1>{{ $t('Your wallet') }}</h1></div><RouterLink :to="{ name: 'settings' }" class="&__account" :aria-label="$t('Account')"><MwAppIcon name="user" /></RouterLink></header>
    <div class="&__overview"><MwBalanceCard /><section class="&__receive"><span class="&__receive-icon"><MwAppIcon name="receive" /></span><h2>{{ $t('Receive with Midori Wallet') }}</h2><p>{{ $t($account.tag ? 'Share your tag with the person sending you money.' : 'Choose a tag to receive from another Midori user.') }}</p><RouterLink :to="{ name: $account.tag ? 'receive' : 'settings.account' }" class="&__tag">{{ $account.tag && $user.username ? `@${$user.username}` : $t('Choose your tag') }}<MwAppIcon name="arrow" /></RouterLink></section></div>
    <section class="&__assets"><div class="&__section-heading"><h2>{{ $t('Stablecoins') }}</h2><span>Base</span></div><div class="&__asset-grid"><RouterLink v-for="item in $cryptos" :key="item.crypto._id" :to="{ name: 'crypto', params: { cryptoId: item.crypto._id } }" class="&__asset"><div class="&__asset-heading"><MwCryptoLogo :crypto="item.crypto" :platform="item.platform" /><span>{{ item.crypto.symbol }}<small>{{ item.crypto.name }}</small></span><MwAppIcon name="arrow" /></div><div class="&__asset-value" dir="ltr">{{ $isHiddenBalance ? '••••' : item.balance?.toString() ?? '—' }}<span>{{ item.crypto.symbol }}</span></div><span class="&__asset-state">{{ item.readState === 'fresh' ? 'Base' : $t(item.balance ? 'Last known balance' : 'Balance unavailable') }}</span></RouterLink></div></section>
    <div v-if="$account.capabilities.ramp" class="&__ramps"><MwAssetAction action="buy" :label="$t('Add money')" icon="plus" /><MwAssetAction action="sell" :label="$t('Withdraw')" icon="send" /></div>
    <section class="&__activity"><div class="&__section-heading"><h2>{{ $t('Activity') }}</h2><RouterLink :to="{ name: 'activity' }">{{ $t('Show') }}<MwAppIcon name="arrow" /></RouterLink></div><MwActivityList compact /></section>
  </div>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app */
.#{ $filename } {
  height: 100%; overflow-y: auto; overscroll-behavior: contain; padding: var(--space-lg); color: var(--color-ink);
  &__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-lg); gap: var(--space-md); }
  &__brand { display: block; color: var(--color-muted); font-size: var(--text-label); margin-bottom: var(--space-2xs); }
  h1 { margin: 0; font-size: var(--text-title); line-height: 1.2; font-weight: 600; letter-spacing: -0.04em; }
  h2 { margin: 0; font-size: 1rem; font-weight: 600; letter-spacing: -0.025em; }
  &__account { display: grid; place-items: center; width: 2.75rem; height: 2.75rem; border: 1px solid var(--color-rule); border-radius: 50%; color: var(--color-ink); background: var(--color-surface); }
  &__account svg { width: 1.125rem; height: 1.125rem; }
  &__overview { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-lg); }
  &__receive { display: none; padding: var(--space-xl); border: 1px solid var(--color-rule); border-radius: var(--radius-hero); background: var(--color-surface); color: var(--color-ink); }
  &__receive-icon { display: grid; place-items: center; width: 2.75rem; height: 2.75rem; border-radius: var(--radius-control); margin-bottom: var(--space-lg); background: var(--color-surface-tint); color: var(--color-accent); }
  &__receive-icon svg { width: 1.25rem; height: 1.25rem; }
  &__receive h2 { max-width: 18ch; font-size: 1.25rem; line-height: 1.3; }
  &__receive p { color: var(--color-muted); max-width: 32ch; font-size: 0.875rem; }
  &__tag { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); font-size: 0.875rem; font-weight: 600; padding-top: var(--space-md); }
  &__tag svg { width: 1.125rem; height: 1.125rem; flex: none; }
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
  &__ramps { display: flex; gap: var(--space-sm); margin-top: var(--space-md); }
  @media (max-width: 47.99rem) {
    &__asset { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 0.85fr); gap: var(--space-xs) var(--space-sm); padding: var(--space-md); }
    &__asset-heading > svg:last-child { display: none; }
    &__asset-value { margin: 0; justify-content: flex-end; align-self: center; font-size: 1.125rem; text-align: end; }
    &__asset-state { grid-column: 1 / -1; font-size: 0.75rem; }
    &__assets, &__activity { margin-top: var(--space-lg); }
  }
  @include breakpoint(sm) { &__asset-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  @include breakpoint(lg) { max-width: 76rem; margin-inline: auto; padding: var(--space-lg) var(--space-xl) var(--space-2xl); &__overview { grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); } &__receive { display: block; } &__header { margin-bottom: var(--space-xl); } }
}
</style>
