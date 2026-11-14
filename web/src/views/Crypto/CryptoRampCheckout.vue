<script>
import { Amount } from '../../lib/account/domain.js';
import MwLoader from '../../components/MwLoader.vue';
import CloseIcon from '../../assets/svg/close.svg';

export default {
  components: { CloseIcon, MwLoader },
  props: {
    order: { type: Object, required: true },
    title: { type: String, required: true },
  },
  emits: ['close', 'update', 'error'],
  data() { return { ready: false, loadTimeout: null }; },
  mounted() {
    this.$refs.dialog.showModal();
    window.addEventListener('message', this.onMessage);
    this.loadTimeout = setTimeout(() => this.$emit('error'), 45000);
    if (this.env.VITE_BUILD_TYPE === 'phonegap') window.backButtonModal = () => this.$emit('close');
  },
  beforeUnmount() {
    window.removeEventListener('message', this.onMessage);
    clearTimeout(this.loadTimeout);
    if (this.env.VITE_BUILD_TYPE === 'phonegap') delete window.backButtonModal;
    this.$refs.dialog.close();
  },
  methods: {
    onMessage(event) {
      if (event.source !== this.$refs.widget?.contentWindow || event.origin !== new URL(this.order.widgetUrl).origin) return;
      const id = event.data?.event_id;
      if (['TRANSAK_WIDGET_INITIALISED', 'TRANSAK_WIDGET_OPEN'].includes(id)) {
        const data = event.data.data;
        const amount = this.order.kind === 'buy' ? 'fiatAmount' : 'cryptoAmount';
        const products = Array.isArray(data?.productsAvailed) ? data.productsAvailed : [data?.productsAvailed];
        let matches = false;
        try {
          matches = data?.partnerOrderId === this.order.id && products.length === 1 && products[0] === this.order.kind.toUpperCase()
            && data.fiatCurrency === this.order.fiatCurrency && data.cryptoCurrencyCode === this.order.cryptoCurrency
            && Amount.fromString(String(data[amount]), 18).value === Amount.fromString(this.order[amount], 18).value;
        } catch { matches = false; }
        if (!matches) this.$emit('error');
        else if (id === 'TRANSAK_WIDGET_OPEN') {
          this.ready = true;
          clearTimeout(this.loadTimeout);
        }
      } else if (id === 'TRANSAK_WIDGET_CLOSE') this.$emit('close');
      else if (['TRANSAK_ORDER_CREATED', 'TRANSAK_ORDER_SUCCESSFUL', 'TRANSAK_ORDER_CANCELLED', 'TRANSAK_ORDER_FAILED'].includes(id)) this.$emit('update');
    },
  },
};
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="&"
      :aria-label="title"
      :aria-busy="!ready"
      @cancel.prevent="$emit('close')"
    >
      <div class="&__header">
        <h2 class="&__title">
          {{ title }} · Transak
        </h2>
        <button
          type="button"
          class="&__close"
          :aria-label="$t('Back')"
          @click="$emit('close')"
        >
          <CloseIcon />
        </button>
      </div>
      <div
        v-if="!ready"
        class="&__loading"
      >
        <MwLoader />
      </div>
      <iframe
        ref="widget"
        :src="order.widgetUrl"
        title="Transak"
        allow="camera; microphone; payment"
        class="&__widget"
        :class="{ '&__widget--loading': !ready }"
        :aria-hidden="!ready"
        :tabindex="ready ? 0 : -1"
      />
    </dialog>
  </Teleport>
</template>

<style lang="scss">
  .#{ $filename } {
    width: 100%;
    max-width: 100%;
    height: 100dvh;
    max-height: 100dvh;
    padding: 0;
    border: none;
    margin: auto;
    background: var(--color-modal-background);
    color: var(--color-text);
    overflow: hidden;

    &[open] {
      display: flex;
      flex-direction: column;
    }

    &::backdrop {
      background: var(--color-modal-overlay);
    }

    @include breakpoint(sm) {
      width: 30rem;
      max-width: calc(100vw - var(--spacing-xl));
      height: 52rem;
      max-height: calc(100dvh - var(--spacing-xl));
      border-radius: 0.625rem;
    }

    &__header {
      display: flex;
      min-height: 3.5rem;
      flex-shrink: 0;
      align-items: center;
      justify-content: space-between;
      padding: var(--spacing-sm) var(--spacing-md);
      padding-top: max(var(--spacing-sm), env(safe-area-inset-top));
      gap: var(--spacing-md);
    }

    &__title {
      @include text-md;
      margin: 0;
      font-weight: var(--font-weight-bold);
    }

    &__close {
      display: grid;
      width: 2.75rem;
      height: 2.75rem;
      flex-shrink: 0;
      border-radius: 0.375rem;
      place-items: center;

      &:focus-visible {
        outline: 2px solid var(--color-primary);
        outline-offset: -2px;
      }

      svg {
        width: var(--spacing-xl);
        height: var(--spacing-xl);
      }
    }

    &__loading {
      position: absolute;
      inset: 50% 0 auto;
      display: flex;
      justify-content: center;
    }

    &__widget {
      width: 100%;
      min-height: 0;
      flex: 1;
      border: none;
      background: var(--color-background);

      &--loading {
        opacity: 0;
        pointer-events: none;
      }
    }
  }
</style>
