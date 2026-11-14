<script>
import { rampError } from '../../lib/account/ramp-errors.js';
import MwButton from '../../components/MwButton.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwFormSelect from '../../components/MwForm/MwFormSelect.vue';
import MwLoader from '../../components/MwLoader.vue';
import CryptoRampOrderRow from './CryptoRampOrderRow.vue';

export default {
  components: {
    MwButton,
    MwFormAmountInput,
    MwFormGroup,
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
      checkoutUrl: '',
      pollTimer: null,
      isLoading: false,
      isLoadingOrders: true,
      error: '',
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
    this.pollTimer = setInterval(() => { if (!document.hidden && this.orders.some(order => !['completed','failed','refunded','expired'].includes(order.status))) this.loadOrders(); }, 15000);
    window.addEventListener('focus', this.loadOrders);
  },
  beforeUnmount() {
    clearInterval(this.pollTimer);
    window.removeEventListener('focus', this.loadOrders);
  },
  methods: {
    async loadOptions() {
      try {
        this.options = await this.$wallet.rampOptions();
        if (!this.currencies.some(item => item.value === this.fiatCurrency)) this.fiatCurrency = this.currencies.find(item => item.value === 'USD')?.value || this.currencies[0]?.value || '';
      } catch (error) { this.error = rampError(error, this.$t); }
    },
    reset() { this.amountValue = undefined; this.error = ''; this.request = null; this.checkoutUrl = ''; },
    async loadOrders() {
      this.isLoadingOrders = true;
      try { this.orders = (await this.$wallet.rampOrders()).orders.filter(order => order.cryptoCurrency === this.$wallet.crypto.symbol); }
      catch (error) { this.error = rampError(error, this.$t); }
      finally { this.isLoadingOrders = false; }
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
        this.amountValue = undefined; this.request = null;
        this.upsertOrder(order);
        this.checkoutUrl = order.widgetUrl;
        this.$safeOpen(order.widgetUrl);
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
  <MwFormGroup class="&__form">
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
      @update:modelValue="error = ''; request = null; checkoutUrl = ''"
    />
  </MwFormGroup>
  <MwButton
    type="primary"
    :isLoading="isLoading"
    :disabled="!available"
    @click="submit"
  >
    {{ kind === 'buy' ? $t('Continue to buy') : $t('Continue to sell') }}
  </MwButton>

  <a v-if="checkoutUrl" :href="checkoutUrl" target="_blank" rel="noopener noreferrer" class="&__checkout">
    {{ kind === 'buy' ? $t('Continue to buy') : $t('Continue to sell') }} · Transak
  </a>
  <div class="&__orders">
    <div class="&__orders-header">
      <h2 class="&__orders-title">
        {{ $t('Recent orders') }}
      </h2>
      <MwButton
        type="secondary"
        small
        :disabled="isLoadingOrders"
        @click="loadOptions(); loadOrders()"
      >
        {{ $t('Refresh') }}
      </MwButton>
    </div>
    <MwLoader v-if="isLoadingOrders" />
    <div
      v-else-if="!orders.length"
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

    &__checkout {
      display: block;
      margin-top: var(--spacing-md);
      text-align: center;
    }

    &__form {
      flex-grow: 1;
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
  }
</style>
