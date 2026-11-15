<script>
import MainLayout from '../../../layouts/MainLayout.vue';
import MwFlowProgress from '../../../components/MwFlowProgress.vue';
import MwCryptoLogo from '../../../components/MwCryptoLogo.vue';
export default {
  components: { MainLayout, MwFlowProgress, MwCryptoLogo },
  props: { title: { type: String, required: true }, current: { type: Number, default: 0 }, storage: { type: Object, required: true } },
  emits: ['back'],
  computed: {
    asset() { return this.$cryptos.find(item => item.crypto._id === this.$wallet.crypto._id); },
    balance() { return this.$isHiddenBalance ? '••••' : this.asset?.balance?.toString() ?? '—'; },
  },
};
</script>
<template>
  <MainLayout class="&" :data-context="current >= 2 ? 'compact' : 'full'" :title="title" wide allowReadFailure :onBack="() => $emit('back')">
    <MwFlowProgress :current="current" />
    <div class="&__grid">
      <aside class="&__context" :aria-label="$t('Wallet')"><div class="&__asset"><MwCryptoLogo :crypto="$wallet.crypto" :platform="$wallet.platform" /><div><strong>{{ $wallet.crypto.symbol }}</strong><small>{{ $wallet.crypto.name }} · Base</small></div></div><div class="&__balance"><div><small>{{ $t('Balance') }}</small><span dir="ltr">{{ balance }} <small v-if="asset?.balance && !$isHiddenBalance">{{ $wallet.crypto.symbol }}</small></span><small v-if="asset?.readState !== 'fresh'">{{ $t(asset?.balance ? 'Last known balance' : 'Balance unavailable') }}</small></div><slot name="balance-action" /></div><div v-if="storage.address" class="&__recipient"><small>{{ $t('To') }}</small><strong>{{ storage.alias || storage.address }}</strong><details v-if="storage.alias"><summary>{{ $t('View wallet address') }}</summary><span dir="ltr">{{ storage.address }}</span></details></div></aside>
      <div class="&__body"><slot /><footer v-if="$slots.actions" class="&__actions"><slot name="actions" /></footer></div>
    </div>
  </MainLayout>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app · pre-emit critique: P5 H5 E5 S5 R5 V4 */
.#{ $filename } {
  &__grid { display: grid; grid-template-columns: minmax(0, 1fr); grid-template-rows: auto 1fr; gap: var(--space-lg); flex: 1; }
  &__context { min-width: 0; border-bottom: 1px solid var(--color-rule); padding-bottom: var(--space-lg); }
  &__asset { display: flex; align-items: center; gap: var(--space-sm); }
  &__asset .mw-crypto-logo { width: 2.5rem; height: 2.5rem; flex: none; }
  &__asset strong { font-size: 1rem; font-weight: 600; }
  &__context small { display: block; font-size: var(--text-label); color: var(--color-muted); line-height: 1.5; }
  &__balance { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); margin-top: var(--space-md); }
  &__balance span { display: block; margin-top: var(--space-2xs); font-size: 1.125rem; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
  &__balance span small { display: inline; }
  &__recipient { padding-top: var(--space-md); margin-top: var(--space-md); border-top: 1px solid var(--color-rule); font-size: 0.875rem; overflow-wrap: anywhere; }
  &__recipient strong { display: block; margin-top: var(--space-2xs); font-weight: 550; }
  &__recipient summary { min-height: 2.75rem; display: flex; align-items: center; color: var(--color-accent); cursor: pointer; }
  &__recipient details span { display: block; padding-bottom: var(--space-xs); }
  &__body { display: flex; flex-direction: column; min-width: 0; gap: var(--space-lg); }
  &__actions { display: flex; flex-direction: column; gap: var(--space-sm); margin-top: auto; padding-top: var(--space-md); }
  @media (max-width: 63.99rem) { &[data-context='compact'] &__asset, &[data-context='compact'] &__balance { display: none; } &[data-context='compact'] &__context { padding-bottom: var(--space-sm); } &[data-context='compact'] &__recipient { margin: 0; padding: 0; border: 0; } &[data-context='compact'] &__grid, &[data-context='compact'] &__body { gap: var(--space-md); } }
  @media (min-width: 64rem) { &__grid { grid-template-columns: minmax(0, 1.35fr) minmax(0, 0.85fr); grid-template-areas: 'body context'; grid-template-rows: auto; gap: var(--space-2xl); } &__body { grid-area: body; } &__context { grid-area: context; align-self: start; border-bottom: 0; border-inline-start: 1px solid var(--color-rule); padding-inline-start: var(--space-lg); } }
  .app-layout[data-runtime='phonegap'] &__grid { grid-template-columns: minmax(0, 1fr); grid-template-areas: none; grid-template-rows: auto 1fr; gap: var(--space-lg); }
  .app-layout[data-runtime='phonegap'] &__body, .app-layout[data-runtime='phonegap'] &__context { grid-area: auto; }
  .app-layout[data-runtime='phonegap'] &__context { border-inline-start: 0; border-bottom: 1px solid var(--color-rule); padding-inline-start: 0; }
}
</style>
