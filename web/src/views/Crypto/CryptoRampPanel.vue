<script>
import { Amount } from '../../lib/account/domain.js';
import { rampError } from '../../lib/account/ramp-errors.js';
import MwButton from '../../components/MwButton.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwLoader from '../../components/MwLoader.vue';
import CryptoRampOrderRow from './CryptoRampOrderRow.vue';

export default {
  components: {
    MwButton,
    MwFormAmountInput,
    MwFormGroup,
    MwLoader,
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
      isLoading: false,
      isLoadingOrders: true,
      error: '',
      request: null,
      orders: [],
    };
  },
  computed: {
    decimals() { return this.kind === 'buy' ? 2 : this.$wallet.crypto.decimals; },
    symbol() { return this.kind === 'buy' ? this.$currency : this.$wallet.crypto.symbol; },
  },
  mounted() {
    this.loadOrders();
  },
  methods: {
    async loadOrders() {
      this.isLoadingOrders = true;
      try { this.orders = (await this.$wallet.rampOrders()).orders; }
      catch { this.orders = []; }
      finally { this.isLoadingOrders = false; }
    },
    upsertOrder(order) {
      const index = this.orders.findIndex(item => item.id === order.id);
      if (index === -1) this.orders.unshift(order);
      else this.orders.splice(index, 1, order);
    },
    async submit() {
      if (this.isLoading) return;
      this.isLoading = true; this.error = '';
      try {
        if (!this.amountValue || this.amountValue.value <= 0n) throw { code: 'INVALID_REQUEST' };
        const payload = {
          kind: this.kind,
          cryptoCurrency: this.$wallet.crypto.symbol,
          fiatCurrency: this.$currency,
          ...(this.kind === 'buy' ? { fiatAmount: this.amountValue.toString() } : { cryptoAmount: this.amountValue.toString() }),
        };
        const fingerprint = JSON.stringify(payload);
        if (this.request?.fingerprint !== fingerprint) this.request = { fingerprint, id: crypto.randomUUID() };
        const order = await this.$wallet.reviewRamp({ ...payload, idempotencyKey: this.request.id });
        this.amountValue = undefined; this.request = null;
        this.upsertOrder(order);
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
  <MwFormGroup class="&__form">
    <MwFormAmountInput
      v-model="amountValue"
      :label="kind === 'buy' ? $t('You pay') : $t('You sell')"
      :error="error"
      :decimals="decimals"
      :symbol="symbol"
      @update:modelValue="error = ''; request = null"
    />
  </MwFormGroup>
  <MwButton
    type="primary"
    :isLoading="isLoading"
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
        :disabled="isLoadingOrders"
        @click="loadOrders"
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
