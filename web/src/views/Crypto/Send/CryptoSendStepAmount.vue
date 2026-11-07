<script>
import { Amount } from '../../../lib/account/domain.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormAmountInput from '../../../components/MwForm/MwFormAmountInput.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwFormAmountInput, MwFormGroup }, extends: MwStep,
  data() { return { amountValue: this.storage.amount || this.storage.temp?.amount, maximum: false, feeMode: this.storage.feeMode || 'token', isLoading: false, error: '', request: null }; },
  methods: {
    async setMaxAmount() {
      try { await this.$loadWallet(); this.amountValue = this.$wallet.balance; this.maximum = true; this.request = null; }
      catch (error) { this.error = paymentError(error, this.$t, { symbol: this.$wallet.crypto.symbol }); }
    },
    async confirm() {
      if (this.isLoading) return; this.isLoading = true; this.error = '';
      try {
        if (!this.maximum && (!this.amountValue || this.amountValue.value <= 0n)) throw { code: 'INVALID_REQUEST' };
        if (this.storage.intent?.status === 'quoted') await this.$wallet.cancelPayment(this.storage.intent.id);
        const recipient = this.storage.recipient;
        if (!recipient) throw { code: 'TAG_RECONFIRM_REQUIRED' };
        // Network cost is paid in the same token being sent: detect it from
        // the asset currently open ($wallet.crypto), instead of a fixed gas
        // token. The sender's own balance already covers amount + fee.
        const payload = { contract: this.$wallet.crypto.address, amountAtomic: this.maximum ? 'max' : this.amountValue.value.toString(), recipient: recipient.kind === 'midori_tag' ? { kind: 'midori_tag', resolutionId: recipient.resolutionId } : { kind: 'address', address: recipient.address }, feeMode: this.feeMode, feeToken: this.feeMode === 'token' ? this.$wallet.crypto.address : null, maxFeeAtomic: this.feeMode === 'token' ? this.$account.paymentConfig.maxFeeAtomic : '0' };
        const fingerprint = JSON.stringify(payload);
        if (this.request?.fingerprint !== fingerprint) this.request = { fingerprint, id: crypto.randomUUID() };
        const intent = await this.$wallet.reviewPayment({ ...payload, idempotencyKey: this.request.id });
        if (intent.status !== 'quoted') {
          if (['authorized','submitted','ambiguous','included','preparing'].includes(intent.status)) { this.updateStorage({ intent }); this.next('status'); return; }
          this.request = null; throw { code: intent.failureCode || 'QUOTE_EXPIRED' };
        }
        this.updateStorage({ intent, amount: new Amount(intent.amountAtomic, 6) }); this.next('confirm');
      } catch (error) {
        if (error.details?.intentId) { try { const intent = await this.$wallet.payment(error.details.intentId); this.updateStorage({ intent }); this.next('status'); return; } catch (lookupError) { this.error = paymentError(lookupError, this.$t, { symbol: this.$wallet.crypto.symbol }); return; } }
        this.error = paymentError(error, this.$t, { symbol: this.$wallet.crypto.symbol });
      } finally { this.isLoading = false; }
    },
  },
};
</script>
<template>
  <MainLayout :title="$t('Send {symbol}', { symbol: $wallet.crypto.symbol })" allowReadFailure>
    <MwFormGroup class="&__content">
      <p>{{ $t('To') }}: {{ storage.alias || storage.address }}</p>
      <MwFormAmountInput v-model="amountValue" :label="$t('Amount')" :error="error" :decimals="6" :symbol="$wallet.crypto.symbol" @update:modelValue="maximum = false; error = ''; request = null" />
      <MwButton type="secondary" :disabled="isLoading" @click="setMaxAmount">{{ $t('max') }}</MwButton>
      <MwButtonGroup v-if="$account.capabilities.sponsorship">
        <MwButton :type="feeMode === 'token' ? 'primary' : 'primary-light'" @click="feeMode = 'token'; request = null">{{ $t('Pay network cost in {symbol}', { symbol: $wallet.crypto.symbol }) }}</MwButton>
        <MwButton :type="feeMode === 'sponsored' ? 'primary' : 'primary-light'" @click="feeMode = 'sponsored'; request = null">{{ $t('Sponsored network cost') }}</MwButton>
      </MwButtonGroup>
    </MwFormGroup>
    <MwButton type="primary" :isLoading="isLoading" @click="confirm">{{ $t('Review payment') }}</MwButton>
  </MainLayout>
</template>
<style lang="scss">.#{ $filename } { &__content { flex-grow: 1; } }</style>
