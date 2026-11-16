<script>
import { clearRecovery } from '../../lib/account/recovery.js';
import MwButton from '../../components/MwButton.vue';
import MwAppIcon from '../../components/MwAppIcon.vue';
import MwStep from '../../components/MwStep.vue';
import { onShowOnHide } from '../../lib/mixins.js';
import LogoIcon from '../../assets/svg/logo.svg';
export default {
  components: { MwButton, MwAppIcon, LogoIcon }, extends: MwStep, mixins: [onShowOnHide],
  computed: { copyright() { return `© ${new Date().getFullYear()} Midori Wallet`; } },
  onShow() { clearRecovery(); window.systemBars?.setStyle('dark'); document.documentElement.dataset.statusbarStyle = 'locked'; },
  onHide() { window.systemBars?.setStyle(this.$resolvedTheme); delete document.documentElement.dataset.statusbarStyle; },
};
</script>
<template>
  <div class="&">
    <section class="&__story"><LogoIcon class="&__logo" /><div class="&__message"><div class="&__coins"><span>USDC</span><span>USDT</span></div><h1>{{ $t('Stablecoins, in your hands.') }}</h1><p>{{ $t('Send and receive USDC and USDT with your own wallet.') }}</p></div><span class="&__network">Base · Midori Wallet</span></section>
    <section class="&__start"><div class="&__actions"><span class="&__mark"><MwAppIcon name="receive" /></span><h2>{{ $t('Your wallet') }}</h2><p>{{ $t('Start with USDC and USDT.') }}</p><MwButton type="primary" @click="updateStorage({ flow: 'create', recoveryHandle: undefined }); next('passphraseGeneration')">{{ $t('Create new wallet') }}<MwAppIcon name="arrow" /></MwButton><MwButton type="secondary" @click="updateStorage({ flow: 'recover', recoveryHandle: undefined }); next('login')">{{ $t('Open existing wallet') }}</MwButton><div class="&__ownership"><MwAppIcon name="check" /><span>{{ $t('Your keys. Your control.') }}</span></div></div><small class="&__copyright">{{ copyright }}</small></section>
  </div>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app */
.#{ $filename } {
  display: grid; grid-template-columns: minmax(0, 1fr); height: 100%; overflow-y: auto; background: var(--color-paper); color: var(--color-ink);
  &__story { display: flex; flex-direction: column; min-width: 0; padding: max(var(--space-lg), env(safe-area-inset-top)) var(--space-lg) var(--space-xl); background: var(--color-brand-panel); color: var(--color-brand-panel-ink); }
  &__logo { width: 8.25rem; height: auto; }
  &__message { padding-block: var(--space-xl) 0; max-width: 34rem; }
  &__coins { display: flex; gap: var(--space-xs); margin-bottom: var(--space-lg); }
  &__coins span { border: 1px solid var(--color-brand-panel-muted); border-radius: var(--radius-control); padding: var(--space-xs) var(--space-sm); font-size: 0.6875rem; color: var(--color-brand-panel-muted); }
  h1 { font-size: clamp(2rem, 4vw, 4.25rem); font-weight: 500; letter-spacing: -0.055em; line-height: 1.06; margin: 0; max-width: 10ch; text-wrap: balance; }
  &__message p { font-size: 0.875rem; color: var(--color-brand-panel-muted); max-width: 35ch; line-height: 1.7; margin-block: var(--space-md) 0; }
  &__network { display: none; color: var(--color-brand-panel-muted); font-size: 0.75rem; }
  &__start { display: flex; flex-direction: column; min-width: 0; justify-content: center; padding: var(--space-xl) var(--space-lg) max(var(--space-lg), env(safe-area-inset-bottom)); }
  &__actions { display: flex; flex-direction: column; gap: var(--space-sm); width: 100%; max-width: 23rem; margin: auto; }
  &__mark { display: none; place-items: center; width: 3rem; height: 3rem; margin-bottom: var(--space-md); border-radius: var(--radius-control); color: var(--color-accent); background: var(--color-surface-tint); }
  &__mark svg { width: 1.5rem; height: 1.5rem; }
  h2 { font-size: 1.625rem; font-weight: 550; letter-spacing: -0.04em; margin: 0; }
  &__actions > p { margin-block: var(--space-2xs) var(--space-lg); color: var(--color-muted); font-size: 0.9375rem; }
  &__ownership { display: flex; align-items: center; justify-content: center; gap: var(--space-xs); color: var(--color-muted); font-size: 0.75rem; margin-top: var(--space-md); }
  &__ownership svg { width: 1rem; height: 1rem; }
  &__copyright { margin-top: var(--space-xl); color: var(--color-muted); font-size: 0.625rem; text-align: center; }
  @include breakpoint(lg) { grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr); &__story { padding: var(--space-2xl); justify-content: space-between; } &__message { padding-block: var(--space-2xl); } &__message p { font-size: 1.0625rem; } &__network { display: block; } &__start { padding: var(--space-2xl); } &__mark { display: grid; } }
}
</style>
