<script>
import { stablecoinFamily, canonicalStablecoin } from '../../../lib/account/stablecoin-accounts.js';
import EvmSendView from './EvmSendView.vue';
import SparkSendView from './SparkSendView.vue';
import BitcoinSendView from './BitcoinSendView.vue';
import { savePaymentDraft, takePaymentDraft } from '../../../lib/navigation.js';
import MainLayout from '../../../layouts/MainLayout.vue';
import MwButton from '../../../components/MwButton.vue';
import MwLoader from '../../../components/MwLoader.vue';
import { exchangeError } from '../../../lib/account/exchange-errors.js';
import { Amount } from '../../../lib/account/domain.js';

import CryptoSendStepPin from './CryptoSendStepPin.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoSendStepAddress from './CryptoSendStepAddress.vue';
import CryptoSendStepAmount from './CryptoSendStepAmount.vue';
import CryptoSendStepConfirm from './CryptoSendStepConfirm.vue';
import CryptoSendStepGas from './CryptoSendStepGas.vue';
import CryptoSendStepMecto from './CryptoSendStepMecto.vue';
import CryptoSendStepMeta from './CryptoSendStepMeta.vue';
import CryptoSendStepMinerFee from './CryptoSendStepMinerFee.vue';
import CryptoSendStepPoor from './CryptoSendStepPoor.vue';
import CryptoSendStepQr from './CryptoSendStepQr.vue';
import CryptoSendStepStatus from './CryptoSendStepStatus.vue';

export default {
  components: {
    EvmSendView, MwSteps, MainLayout, MwButton, MwLoader, BitcoinSendView, SparkSendView,
  },
  computed: { usdcOnly() { return stablecoinFamily(this.$wallet.crypto) === 'USDT'; }, usdc() { return this.$account.cryptoDB.all.find(crypto => crypto.symbol === 'USDC' && crypto.platform === this.$wallet.crypto.platform) || canonicalStablecoin(this.$account.cryptoDB.all, 'USDC'); } },
  data() {
    const owner = this.$wallet.address;
    const path = this.$route.fullPath;
    const draft = this.$route.query.exchange ? null : takePaymentDraft(owner, path);
    const temp = this.parseQuery(this.$route);
    if (draft) {
      temp.address = draft.address;
      temp.amount = draft.amount === undefined ? undefined : Amount.fromString(draft.amount, this.$wallet.crypto.decimals);
    }
    return { owner, draftPath: path, storage: { temp }, ready: !this.$route.query.exchange, exchangeError: '', disposed: false };
  },
  mounted() { if (!this.$wallet.isEvm && !['bitcoin','spark'].includes(this.$wallet.crypto.platform) && this.$route.query.exchange) this.loadExchange(); },
  beforeUnmount() {
    this.disposed = true;
    const steps = this.$refs.steps;
    const active = steps?.activeStep;
    if (!steps || !this.$account.isCreated || ['confirmed','reverted','expired'].includes(steps.storage.intent?.status)) return;
    const address = typeof active?.address === 'string' ? active.address : steps.storage.alias || steps.storage.address || steps.storage.temp?.address || '';
    const amount = active && 'amountValue' in active ? active.amountValue : steps.storage.amount ?? steps.storage.temp?.amount;
    savePaymentDraft(this.owner, this.draftPath, { address, amount: amount?.toString() });
  },
  steps: {
    index: CryptoSendStepAddress,
    meta: CryptoSendStepMeta,
    fee: CryptoSendStepMinerFee,
    gas: CryptoSendStepGas,
    amount: CryptoSendStepAmount,
    confirm: CryptoSendStepConfirm,
    status: CryptoSendStepStatus,
    mecto: CryptoSendStepMecto,
    pin: CryptoSendStepPin,
    qr: CryptoSendStepQr,
    poor: CryptoSendStepPoor,
  },
  methods: {
    async loadExchange() {
      this.exchangeError = '';
      try {
        const order = await this.$wallet.exchangeOrder(this.$route.query.exchange);
        if (this.disposed) return;
        if (!order.depositAddress || order.quote.from.contract !== this.$wallet.crypto.address.toLowerCase()) throw { code: 'INVALID_REQUEST' };
        this.storage = { exchangeOrderId: order.id, exchange: order, temp: { address: order.depositAddress, amount: Amount.fromString(order.quote.amountFrom, 6) } };
        if (order.paymentIntentId) {
          this.storage.intent = await this.$wallet.payment(order.paymentIntentId);
          if (this.disposed) return;
        }
        this.ready = true;
      } catch (error) { if (!this.disposed) this.exchangeError = exchangeError(error, this.$t); }
    },
    parseQuery(route) {
      const temp = {};
      if (route.query.address) {
        temp.address = route.query.address;
      }
      if (route.query.amount) {
        try {
          temp.amount = Amount.fromString(route.query.amount, this.$wallet.crypto.decimals);
        } catch (err) {
          console.error('Midori Wallet operation failed');
        }
      }
      if (route.query.destinationTag && this.$wallet.crypto._id === 'xrp@ripple') {
        temp.meta = {
          destinationTag: route.query.destinationTag,
        };
      }
      return temp;
    },
  },
};
</script>

<template>
  <MainLayout v-if="usdcOnly" :title="$t('Send {symbol}', { symbol: 'USDC' })" allowReadFailure><p>{{ $t('Only USDC can be sent.') }}</p><MwButton type="primary" @click="$router.replace({ name: 'crypto.send', params: { cryptoId: usdc._id } })">{{ $t('Send {symbol}', { symbol: 'USDC' }) }}</MwButton><MwButton type="secondary" @click="$router.replace({ name: 'crypto.history', params: $route.params })">{{ $t('History') }}</MwButton></MainLayout>
  <EvmSendView v-else-if="$wallet.isEvm" />
  <SparkSendView v-else-if="$wallet.crypto.platform === 'spark'" />
  <BitcoinSendView v-else-if="$wallet.crypto.platform === 'bitcoin'" />
  <MwSteps
    v-else-if="ready"
    ref="steps"
    :step="storage.intent ? (storage.intent.status === 'quoted' ? 'confirm' : 'status') : 'index'"
    :steps="$options.steps"
    :initialStorage="storage"
  />
  <MainLayout v-else :title="$t('Review deposit')" allowReadFailure><p v-if="exchangeError" role="alert">{{ exchangeError }}</p><MwButton v-if="exchangeError" type="secondary" @click="loadExchange">{{ $t('Refresh') }}</MwButton><MwLoader v-else /></MainLayout>
</template>
