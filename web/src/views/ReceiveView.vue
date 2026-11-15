<script>
import MainLayout from '../layouts/MainLayout.vue';
import MwCryptoLogo from '../components/MwCryptoLogo.vue';
import MwAppIcon from '../components/MwAppIcon.vue';
export default { components: { MainLayout, MwCryptoLogo, MwAppIcon } };
</script>
<template>
  <MainLayout :title="$t('Receive')" allowReadFailure>
    <div class="&__intro"><span><MwAppIcon name="receive" /></span><h1>{{ $t('Receive with Midori Wallet') }}</h1><p>{{ $t('USDT and USDC on Base share your account address.') }}</p></div>
    <div class="&__assets"><RouterLink v-for="item in $cryptos" :key="item.crypto._id" :to="{ name: 'crypto.receive', params: { cryptoId: item.crypto._id } }"><MwCryptoLogo :crypto="item.crypto" :platform="item.platform" /><span>{{ item.crypto.symbol }}<small>{{ item.crypto.name }} · Base</small></span><MwAppIcon name="arrow" /></RouterLink></div>
    <RouterLink v-if="!$account.tag && $cryptos.length" :to="{ name: 'crypto.receive', params: { cryptoId: $cryptos[0].crypto._id }, query: { source: 'midori' } }" class="&__tag">{{ $t('Choose your tag') }}<MwAppIcon name="arrow" /></RouterLink>
  </MainLayout>
</template>
<style lang="scss">
.#{ $filename } {
  &__intro > span { display: grid; place-items: center; width: 3rem; height: 3rem; border-radius: var(--radius-control); background: var(--color-surface-tint); color: var(--color-accent); }
  &__intro svg { width: 1.5rem; height: 1.5rem; }
  &__intro h1 { font-size: 1.75rem; font-weight: 550; letter-spacing: -0.04em; line-height: 1.25; margin-block: var(--space-lg) var(--space-sm); }
  &__intro p { font-size: 0.9375rem; color: var(--color-muted); max-width: 42ch; margin: 0; }
  &__assets { display: flex; flex-direction: column; gap: var(--space-sm); }
  &__assets a { display: flex; align-items: center; gap: var(--space-md); min-height: 5rem; padding: var(--space-md); border: 1px solid var(--color-rule); border-radius: var(--radius-panel); background: var(--color-surface); color: var(--color-ink); }
  &__assets a:hover { border-color: var(--color-accent); }
  &__assets a > .mw-crypto-logo { width: 2.75rem; height: 2.75rem; flex: none; }
  &__assets a > span { flex: 1; font-weight: 600; }
  &__assets a small { display: block; font-size: var(--text-label); font-weight: 400; color: var(--color-muted); margin-top: var(--space-2xs); }
  &__assets a > svg:last-child, &__tag svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__tag { display: flex; align-items: center; justify-content: space-between; min-height: 2.75rem; gap: var(--space-sm); font-size: 0.875rem; }
}
</style>
