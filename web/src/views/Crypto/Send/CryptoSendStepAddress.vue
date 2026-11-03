<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import PasteIcon from '../../../assets/svg/paste.svg';
import QrIcon from '../../../assets/svg/qr.svg';
import { isQrScanAvailable } from '../../../lib/helpers.js';
import { onShowOnHide } from '../../../lib/mixins.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
import { Amount } from '../../../lib/account/domain.js';
export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwFormGroup, MwFormInput, PasteIcon, QrIcon },
  extends: MwStep, mixins: [onShowOnHide],
  data() { return { address: '', error: '', isLoading: false, isPasteAvailable: !!navigator.clipboard?.readText, isQrScanAvailable: false }; },
  async onShow() {
    if (this.storage.temp?.address) { this.address = this.storage.temp.address; this.storage.temp.address = undefined; }
    this.isQrScanAvailable = await isQrScanAvailable();
    try {
      const { intents } = await this.$wallet.pendingPayments();
      const intent = intents.find(item => ['preparing','quoted','authorized','submitted','ambiguous','included','reorganized'].includes(item.status));
      if (intent) {
        if (intent.contract.toLowerCase() !== this.$wallet.crypto.address.toLowerCase()) { const wallet = this.$account.wallets().find(item => item.crypto.address.toLowerCase() === intent.contract.toLowerCase());
          if (wallet) this.$router.replace({ name: 'crypto.send', params: { cryptoId: wallet.crypto._id } });
          return; }
        this.updateStorage({ intent, address: intent.to, alias: intent.recipientTag ? `@${intent.recipientTag}` : '', amount: new Amount(intent.amountAtomic, 6) });
        this.replace(intent.status === 'quoted' ? 'confirm' : 'status');
      }
    } catch { /* The recipient can still be entered while reconnecting. */ }
  },
  methods: {
    async confirm() {
      if (this.isLoading) return;
      this.isLoading = true; this.error = '';
      try {
        const recipient = await this.$wallet.resolveRecipient(this.address);
        this.updateStorage({ address: recipient.address, alias: recipient.alias || '', recipient, intent: undefined, temp: this.storage.temp });
        this.next('amount');
      } catch (error) { this.error = paymentError(error, this.$t); }
      finally { this.isLoading = false; }
    },
    async paste() { try { this.address = await navigator.clipboard.readText(); this.error = ''; } catch { /* Clipboard permission can be declined. */ } },
  },
};
</script>
<template>
  <MainLayout :title="$t('Send {symbol}', { symbol: $wallet.crypto.symbol })" allowReadFailure>
    <MwFormGroup class="&__container">
      <MwFormInput v-model="address" :label="$t('Wallet address or Midori tag')" placeholder="@usuario / 0x…" :error="error" :clear="true" @update:modelValue="error = ''" />
      <MwButtonGroup class="&__actions" type="circle">
        <MwButton v-if="isPasteAvailable" type="circle" @click="paste"><template #circle><PasteIcon /></template>{{ $t('Paste') }}</MwButton>
        <MwButton v-if="isQrScanAvailable" type="circle" @click="next('qr')"><template #circle><QrIcon /></template>{{ $t('Scan QR') }}</MwButton>
      </MwButtonGroup>
    </MwFormGroup>
    <MwButton type="primary" :isLoading="isLoading" @click="confirm">{{ $t('Continue') }}</MwButton>
  </MainLayout>
</template>
<style lang="scss">
.#{ $filename } { &__container { flex-grow: 1; } &__actions { width: 100%; max-width: 25rem; align-self: center; } }
</style>
