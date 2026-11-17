<script>
import CryptoSendLayout from './CryptoSendLayout.vue';
import { onShowOnHide } from '../../../lib/mixins.js';
import { Amount } from '../../../lib/account/domain.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormAmountInput from '../../../components/MwForm/MwFormAmountInput.vue';
import MwStep from '../../../components/MwStep.vue';
export default {
  components: { CryptoSendLayout, MwButton, MwButtonGroup, MwFormAmountInput }, extends: MwStep, mixins: [onShowOnHide],
  data() { return { amountValue: this.storage.amount || this.storage.temp?.amount, maximum: false, feeMode: this.storage.feeMode || 'token', isLoading: false, error: '', request: null, active: false, generation: 0 }; },
  onShow() { this.active = true; this.generation++; this.amountValue = this.storage.temp?.amount ?? this.storage.amount; if (this.storage.temp) this.storage.temp.amount = undefined; this.maximum = false; this.feeMode = this.storage.feeMode || this.feeMode; this.request = null; },
  onHide() { this.active = false; this.generation++; this.updateStorage({ amount: this.amountValue, feeMode: this.feeMode }); },
  methods: {
    async setMaxAmount() {
      if (this.isLoading) return;
      const generation = this.generation, wallet = this.$wallet; this.isLoading = true;
      try { await this.$loadWallet(); if (!this.active || generation !== this.generation) return; this.amountValue = wallet.balance; this.maximum = true; this.request = null; }
      catch (error) { if (this.active && generation === this.generation) this.error = paymentError(error, this.$t, { symbol: wallet.crypto.symbol }); }
      finally { this.isLoading = false; }
    },
    async confirm() {
      if (this.isLoading || !this.active) return; const generation = this.generation, wallet = this.$wallet; this.isLoading = true; this.error = '';
      try {
        if (!this.maximum && (!this.amountValue || this.amountValue.value <= 0n)) throw { code: 'INVALID_REQUEST' };
        if (this.storage.intent?.status === 'quoted') { const cancelled = await wallet.cancelPayment(this.storage.intent.id); if (!this.active || generation !== this.generation) return; if (!['cancelled', 'expired', 'rejected'].includes(cancelled.status)) { this.updateStorage({ intent: cancelled }); this.next('status'); return; } }
        let recipient = this.storage.recipient;
        // Preserve the resolution and key when retrying a lost review response.
        if (recipient?.kind === 'midori_tag' && !this.request) {
          const current = await wallet.resolveRecipient(this.storage.alias);
          if (current.address.toLowerCase() !== recipient.address.toLowerCase() || current.revision !== recipient.revision) throw { code: 'TAG_RECONFIRM_REQUIRED' };
          if (!this.active || generation !== this.generation) return; recipient = current; this.updateStorage({ recipient });
        }
        if (!recipient) throw { code: 'TAG_RECONFIRM_REQUIRED' };
        // Network cost is paid in the same token being sent: detect it from
        // the asset currently open ($wallet.crypto), instead of a fixed gas
        // token. The sender's own balance already covers amount + fee.
        const payload = { ...(this.storage.exchangeOrderId ? { exchangeOrderId: this.storage.exchangeOrderId } : {}), contract: this.$wallet.crypto.address, amountAtomic: this.maximum ? 'max' : this.amountValue.value.toString(), recipient: recipient.kind === 'midori_tag' ? { kind: 'midori_tag', resolutionId: recipient.resolutionId } : { kind: 'address', address: recipient.address }, feeMode: this.feeMode, feeToken: this.feeMode === 'token' ? this.$wallet.crypto.address : null, maxFeeAtomic: this.feeMode === 'token' ? this.$account.paymentConfig.maxFeeAtomic : '0' };
        const fingerprint = JSON.stringify(payload);
        if (this.request?.fingerprint !== fingerprint) this.request = { fingerprint, id: crypto.randomUUID() };
        const intent = await this.$wallet.reviewPayment({ ...payload, idempotencyKey: this.request.id });
        if (!this.active || generation !== this.generation) return;
        if (intent.status !== 'quoted') {
          if (['authorized','submitted','ambiguous','included','preparing','reorganized'].includes(intent.status)) { this.updateStorage({ intent }); this.next('status'); return; }
          this.request = null; throw { code: intent.failureCode || 'QUOTE_EXPIRED' };
        }
        this.updateStorage({ intent, amount: new Amount(intent.amountAtomic, 6) }); this.next('confirm');
      } catch (error) {
        if (!this.active || generation !== this.generation) return;
        if (error.details?.intentId) { try { const intent = await wallet.payment(error.details.intentId); if (!this.active || generation !== this.generation) return; this.updateStorage({ intent }); this.next('status'); return; } catch (lookupError) { this.error = paymentError(lookupError, this.$t, { symbol: this.$wallet.crypto.symbol }); return; } }
        this.error = paymentError(error, this.$t, { symbol: this.$wallet.crypto.symbol });
      } finally { this.isLoading = false; }
    },
  },
};
</script>
<template>
  <CryptoSendLayout class="&" :title="$t('Send {symbol}', { symbol: $wallet.crypto.symbol })" :current="1" :storage="storage" @back="back">
    <template #balance-action><MwButton type="primary-link" small :disabled="isLoading" @click="setMaxAmount">{{ $t('max') }}</MwButton></template>
    <fieldset :disabled="isLoading" class="&__fields"><MwFormAmountInput v-model="amountValue" :label="$t('Amount')" :error="error" :decimals="$wallet.crypto.decimals" :symbol="$wallet.crypto.symbol" @update:modelValue="maximum = false; error = ''; request = null" /><MwButtonGroup v-if="$account.capabilities.sponsorship"><MwButton :type="feeMode === 'token' ? 'primary' : 'primary-light'" @click="feeMode = 'token'; request = null">{{ $t('Pay network cost in {symbol}', { symbol: $wallet.crypto.symbol }) }}</MwButton><MwButton :type="feeMode === 'sponsored' ? 'primary' : 'primary-light'" @click="feeMode = 'sponsored'; request = null">{{ $t('Sponsored network cost') }}</MwButton></MwButtonGroup></fieldset>
    <MwButton type="primary-link" small :disabled="isLoading" @click="back">{{ $t('Edit') }} · {{ $t('To') }}</MwButton>
    <template #actions><MwButton type="primary" :isLoading="isLoading" @click="confirm">{{ $t('Review payment') }}</MwButton></template>
  </CryptoSendLayout>
</template>
<style lang="scss">.#{ $filename }__fields { display: flex; flex-direction: column; min-width: 0; border: 0; padding: 0; margin: 0; gap: var(--space-lg); }</style>
