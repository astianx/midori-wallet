<script>
import { rampError } from '../../lib/account/ramp-errors.js';
import MwButton from '../../components/MwButton.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormSelect from '../../components/MwForm/MwFormSelect.vue';
import MwLoader from '../../components/MwLoader.vue';
import CryptoRampOrderRow from './CryptoRampOrderRow.vue';
import eventBus from '../../lib/eventBus.js';

export default {
  components: {
    MwButton,
    MwFormAmountInput,
    MwLoader,
    MwFormSelect,
    CryptoRampOrderRow,
  },
  props: {
    kind: {
      type: String,
      required: true,
      validator: value => ['buy', 'sell'].includes(value),
    },
  },
  data() {
    return {
      amountValue: undefined,
      options: null,
      fiatCurrency: this.$currency,
      disposed: false,
      ordersRequest: null,
      isLoading: false,
      isLoadingOrders: true,
      isRefreshing: false,
      error: '',
      ordersError: '',
      request: null,
      orders: [],
    };
  },
  computed: {
    providerAsset() { return this.options?.assets.find(item => item.symbol === this.$wallet.crypto.symbol); },
    currencies() { return (this.options?.fiatCurrencies || []).filter(item => item[this.kind]).map(item => ({ value: item.code, name: item.code })); },
    available() { return this.options?.assets.some(item => item.symbol === this.$wallet.crypto.symbol && item[this.kind]) && this.currencies.some(item => item.value === this.fiatCurrency); },
    decimals() { return this.kind === 'buy' ? this.options?.fiatCurrencies.find(item => item.code === this.fiatCurrency)?.decimals ?? 2 : Math.min(this.$wallet.crypto.decimals, this.providerAsset?.roundOff ?? this.$wallet.crypto.decimals); },
    symbol() { return this.kind === 'buy' ? this.fiatCurrency : this.$wallet.crypto.symbol; },
  },
  mounted() {
    this.loadOptions();
    this.loadOrders();
    eventBus.on('rampCheckoutUpdate', this.onCheckoutUpdate);
    eventBus.on('rampCheckoutError', this.onCheckoutError);
  },
  beforeUnmount() {
    this.disposed = true;
    eventBus.off('rampCheckoutUpdate', this.onCheckoutUpdate);
    eventBus.off('rampCheckoutError', this.onCheckoutError);
  },
  methods: {
    async loadOptions() {
      try {
        const options = await this.$wallet.rampOptions();
        if (this.disposed) return;
        this.options = options;
        if (!this.currencies.some(item => item.value === this.fiatCurrency)) this.fiatCurrency = this.currencies.find(item => item.value === 'USD')?.value || this.currencies[0]?.value || '';
      } catch (error) { if (!this.disposed) this.error = rampError(error, this.$t); }
    },
    reset() { this.amountValue = undefined; this.error = ''; this.request = null; },
    onCheckoutUpdate(order) {
      if (order.cryptoCurrency === this.$wallet?.crypto.symbol) this.loadOrders();
    },
    onCheckoutError(order) {
      if (order.cryptoCurrency === this.$wallet?.crypto.symbol) this.error = rampError({ code: 'RAMP_PROVIDER_UNAVAILABLE' }, this.$t);
    },
    async refresh() {
      if (this.isRefreshing || this.disposed) return;
      this.isRefreshing = true;
      try { await Promise.all([this.loadOptions(), this.loadOrders()]); }
      finally { if (!this.disposed) this.isRefreshing = false; }
    },
    loadOrders() {
      if (this.disposed) return Promise.resolve();
      if (this.ordersRequest) return this.ordersRequest;
      const wallet = this.$wallet;
      this.ordersError = '';
      this.ordersRequest = Promise.resolve()
        .then(() => wallet.rampOrders())
        .then(data => {
          if (!this.disposed) this.orders = data.orders.filter(order => order.cryptoCurrency === wallet.crypto.symbol);
        })
        .catch(error => { if (!this.disposed) this.ordersError = rampError(error, this.$t); })
        .finally(() => {
          this.ordersRequest = null;
          if (!this.disposed) this.isLoadingOrders = false;
        });
      return this.ordersRequest;
    },
    upsertOrder(order) {
      const index = this.orders.findIndex(item => item.id === order.id);
      if (index === -1) this.orders.unshift(order);
      else this.orders.splice(index, 1, order);
    },
    async submit() {
      if (this.isLoading || !this.available) return;
      this.isLoading = true; this.error = '';
      try {
        if (!this.amountValue || this.amountValue.value <= 0n) throw { code: 'INVALID_REQUEST' };
        const payload = {
          kind: this.kind,
          cryptoCurrency: this.$wallet.crypto.symbol,
          fiatCurrency: this.fiatCurrency,
          ...(this.kind === 'buy' ? { fiatAmount: this.amountValue.toString() } : { cryptoAmount: this.amountValue.toString() }),
        };
        const fingerprint = JSON.stringify(payload);
        if (this.request?.fingerprint !== fingerprint) this.request = { fingerprint, id: crypto.randomUUID() };
        const order = await this.$wallet.reviewRamp({ ...payload, idempotencyKey: this.request.id });
        if (this.disposed) return;
        this.request = null;
        this.upsertOrder(order);
        eventBus.emit('openRampCheckout', order);
      } catch (error) {
        this.error = rampError(error, this.$t);
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<template>
  <MwLoader v-if="!options && !error" />
  <div v-if="options && !available" class="&__notice" role="status">
    {{ kind === 'sell' && !options.products.includes('SELL') ? $t('Buying or selling crypto is temporarily unavailable.') : $t('This asset or currency is unavailable through Transak.') }}
  </div>
  <fieldset
    class="&__form"
    :disabled="isLoading"
  >
    <MwFormSelect
      v-if="currencies.length"
      v-model="fiatCurrency"
      :label="$t('Local currency')"
      :options="currencies"
      @update:modelValue="reset"
    />
    <MwFormAmountInput
      v-model="amountValue"
      :label="kind === 'buy' ? $t('You pay') : $t('You sell')"
      :error="error"
      :decimals="decimals"
      :symbol="symbol"
      @update:modelValue="error = ''; request = null"
    />
  </fieldset>
  <MwButton
    type="primary"
    :isLoading="isLoading"
    :disabled="!available || isLoading"
    @click="submit"
  >
    {{ kind === 'buy' ? $t('Continue to buy') : $t('Continue to sell') }}
  </MwButton>

  <div class="&__orders">
    <div class="&__orders-header">
      <h2 class="&__orders-title">
        {{ $t('Recent orders') }}
      </h2>
      <MwButton
        type="secondary"
        small
        :disabled="isLoadingOrders || isRefreshing || isLoading"
        @click="refresh"
      >
        {{ $t('Refresh') }}
      </MwButton>
    </div>
    <div v-if="ordersError" class="&__error" role="alert">
      {{ ordersError }}
    </div>
    <MwLoader v-if="isLoadingOrders" />
    <div
      v-else-if="!orders.length && !ordersError"
      class="&__empty"
    >
      {{ $t('No orders yet.') }}
    </div>
    <div v-else>
      <CryptoRampOrderRow
        v-for="order in orders"
        :key="order.id"
        :order="order"
      />
    </div>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    &__notice {
      @include text-sm;
      padding: var(--spacing-md);
      margin-bottom: var(--spacing-md);
      background: var(--color-background-secondary);
      border: 1px solid var(--color-secondary);
      border-radius: 0.625rem;
    }

    &__form {
      display: flex;
      min-width: 0;
      flex-grow: 1;
      flex-direction: column;
      padding: 0;
      border: none;
      margin: 0;
      gap: var(--spacing-lg);
    }

    &__orders {
      margin-top: var(--spacing-xl);
    }

    &__orders-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--spacing-sm);
      margin-bottom: var(--spacing-sm);
    }

    &__orders-title {
      @include text-md;
      font-weight: var(--font-weight-bold);
    }

    &__empty {
      @include text-md;
      color: var(--color-secondary);
    }

    &__error {
      @include text-sm;
      margin-bottom: var(--spacing-sm);
      color: var(--color-danger);
    }
  }
</style>
