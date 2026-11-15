<script>
import { Amount } from '../../lib/account/domain.js';
import { rampNetwork } from '../../lib/account/ramp-presentation.js';
import MwButton from '../../components/MwButton.vue';
import MwLoader from '../../components/MwLoader.vue';
import CloseIcon from '../../assets/svg/close.svg';
export default {
  components: { CloseIcon, MwLoader, MwButton },
  props: { order: { type: Object, required: true }, title: { type: String, required: true } },
  emits: ['close', 'update', 'error'],
  data() { return { ready: false, failure: '', loadTimeout: null, previousFocus: null, previousBack: null, nativeBack: null, viewportHeight: 0 }; },
  computed: { network() { return rampNetwork(this.order.environment); } },
  mounted() {
    this.previousFocus = document.activeElement; this.$refs.dialog.showModal(); this.$refs.close.focus({ preventScroll: true });
    window.addEventListener('message', this.onMessage); window.addEventListener('offline', this.offline);
    window.visualViewport?.addEventListener('resize', this.resize); window.visualViewport?.addEventListener('scroll', this.resize); window.addEventListener('resize', this.resize); this.resize();
    this.loadTimeout = setTimeout(() => this.fail('timeout'), 45000);
    if (this.env.VITE_BUILD_TYPE === 'phonegap') { this.previousBack = window.backButtonModal; this.nativeBack = () => this.$emit('close'); window.backButtonModal = this.nativeBack; }
  },
  beforeUnmount() {
    window.removeEventListener('message', this.onMessage); window.removeEventListener('offline', this.offline); clearTimeout(this.loadTimeout);
    window.visualViewport?.removeEventListener('resize', this.resize); window.visualViewport?.removeEventListener('scroll', this.resize); window.removeEventListener('resize', this.resize);
    if (window.backButtonModal === this.nativeBack) { if (this.previousBack) window.backButtonModal = this.previousBack; else delete window.backButtonModal; }
    this.$refs.dialog.close(); if (this.previousFocus?.isConnected) this.previousFocus.focus({ preventScroll: true });
  },
  methods: {
    resize() { this.viewportHeight = Math.round(window.visualViewport?.height || window.innerHeight); },
    offline() { this.fail('connection'); },
    fail(reason) { if (this.failure) return; this.failure = reason; this.ready = false; clearTimeout(this.loadTimeout); this.$emit('error', reason); this.$nextTick(() => this.$refs.return?.$el.focus({ preventScroll: true })); },
    onMessage(event) {
      if (this.failure || event.source !== this.$refs.widget?.contentWindow || event.origin !== new URL(this.order.widgetUrl).origin) return;
      const id = event.data?.event_id;
      if (['TRANSAK_WIDGET_INITIALISED', 'TRANSAK_WIDGET_OPEN'].includes(id)) {
        const data = event.data.data, amount = this.order.kind === 'buy' ? 'fiatAmount' : 'cryptoAmount';
        const products = Array.isArray(data?.productsAvailed) ? data.productsAvailed : [data?.productsAvailed];
        let matches = false;
        try { matches = data?.partnerOrderId === this.order.id && products.length === 1 && products[0] === this.order.kind.toUpperCase() && data.fiatCurrency === this.order.fiatCurrency && data.cryptoCurrencyCode === this.order.cryptoCurrency && Amount.fromString(String(data[amount]), 18).value === Amount.fromString(this.order[amount], 18).value; } catch { matches = false; }
        if (!matches) this.fail('mismatch');
        else if (id === 'TRANSAK_WIDGET_OPEN') { this.ready = true; clearTimeout(this.loadTimeout); }
      } else if (id === 'TRANSAK_WIDGET_CLOSE') this.$emit('close');
      else if (['TRANSAK_ORDER_CREATED', 'TRANSAK_ORDER_SUCCESSFUL', 'TRANSAK_ORDER_CANCELLED', 'TRANSAK_ORDER_FAILED'].includes(id)) this.$emit('update');
    },
  },
};
</script>
<template>
  <Teleport to="body">
    <dialog ref="dialog" class="&" :data-runtime="env.VITE_BUILD_TYPE" :style="{ '--checkout-viewport': `${viewportHeight}px` }" :aria-label="title" :aria-busy="!ready && !failure" @cancel.prevent="$emit('close')">
      <header class="&__header"><div><h2>{{ title }}</h2><p>Transak · {{ network }} · {{ order.environment }}</p></div><button ref="close" type="button" class="&__close" :aria-label="$t('Return to order')" @click="$emit('close')"><CloseIcon aria-hidden="true" /></button></header>
      <p v-if="order.environment === 'STAGING'" class="&__test" role="status">{{ $t('Test mode') }} · {{ $t('Transak test mode. Use test funds only; orders do not change your Base mainnet balance.') }}</p>
      <div class="&__stage"><div v-if="!ready" class="&__loading" :role="failure ? 'alert' : 'status'">
        <MwLoader v-if="!failure" /><template v-else><p>{{ $t(failure === 'mismatch' ? 'The checkout details did not match. Return to your order.' : failure === 'connection' ? 'The connection was interrupted. Check this order before trying again.' : failure === 'expired' ? 'This session has expired. Return to your order to continue.' : 'Checkout did not load. Return to your order and check its status.') }}</p><MwButton ref="return" type="primary" @click="$emit('close')">{{ $t('Return to order') }}</MwButton></template>
      </div>
      <iframe v-if="!failure" ref="widget" :src="order.widgetUrl" title="Transak" allow="camera; microphone; payment" class="&__widget" :class="{ '&__widget--loading': !ready }" :aria-hidden="!ready" :tabindex="ready ? 0 : -1" @error="fail('load')" />
      </div><footer class="&__footer"><p>{{ $t('Closing Transak does not cancel or confirm this order.') }}</p><button v-if="ready" type="button" @click="fail('expired')">{{ $t('Session expired?') }}</button></footer>
    </dialog>
  </Teleport>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · designed-as-app · pre-emit critique: P5 H5 E4 S5 R5 V4 */
.#{ $filename } {
  position: fixed; inset: 0; width: 100%; max-width: 100%; height: var(--checkout-viewport, 100dvh); max-height: 100dvh; padding: 0; border: 0; margin: auto; background: var(--color-surface-raised); color: var(--color-ink); overflow: hidden;
  &[open] { display: flex; flex-direction: column; }
  &::backdrop { background: var(--color-overlay); }
  &__header { display: flex; flex: none; align-items: center; justify-content: space-between; padding: max(var(--space-xs), env(safe-area-inset-top)) max(var(--space-md), env(safe-area-inset-right)) var(--space-xs) max(var(--space-md), env(safe-area-inset-left)); gap: var(--space-md); }
  &__header > div { min-width: 0; }
  h2 { margin: 0; font-size: 1rem; font-weight: 600; overflow-wrap: anywhere; }
  &__header p { margin: var(--space-2xs) 0 0; font-size: 0.6875rem; color: var(--color-muted); }
  &__close { display: grid; place-items: center; flex: none; width: 2.75rem; height: 2.75rem; border-radius: var(--radius-control); background: var(--color-surface-subtle); }
  &__close:hover { background: var(--color-surface-tint); }
  &__close:active { opacity: 0.8; }
  &__close svg { width: 1.125rem; height: 1.125rem; }
  &__test, &__footer { flex: none; margin: 0; padding: var(--space-xs) var(--space-md); font-size: 0.6875rem; line-height: 1.4; color: var(--color-muted); }
  &__test { background: var(--color-warning-surface); color: var(--color-warning); }
  &__stage { position: relative; display: flex; min-height: 0; flex: 1; }
  &__loading { position: absolute; inset: 0; display: flex; min-height: 0; flex: 1; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-md); padding: var(--space-lg); text-align: center; overflow-y: auto; font-size: var(--text-label); line-height: 1.6; }
  &__widget { width: 100%; min-height: 0; flex: 1; border: 0; background: var(--color-surface); }
  &__widget--loading { opacity: 0; pointer-events: none; }
  &__footer { padding-bottom: max(var(--space-xs), env(safe-area-inset-bottom)); }
  &__footer p { margin: 0; }
  &__footer button { min-height: 2.75rem; color: var(--color-accent); }
  @media (min-width: 48rem) { width: 32rem; max-width: calc(100vw - var(--space-xl)); height: min(52rem, calc(var(--checkout-viewport, 100dvh) - var(--space-xl))); max-height: calc(100dvh - var(--space-xl)); border-radius: var(--radius-panel); }
  &[data-runtime='phonegap'] { width: 100%; max-width: 100%; height: var(--checkout-viewport, 100dvh); max-height: 100dvh; border-radius: 0; }
}
</style>
