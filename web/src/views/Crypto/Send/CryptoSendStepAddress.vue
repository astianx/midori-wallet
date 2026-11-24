<script>
import MwNetworkSelector from '../../../components/MwNetworkSelector.vue';
import { formatMidoriTag } from '../../../lib/tags.js';
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import CryptoSendLayout from './CryptoSendLayout.vue';
import PasteIcon from '../../../assets/svg/paste.svg';
import QrIcon from '../../../assets/svg/qr.svg';
import { isQrScanAvailable } from '../../../lib/helpers.js';
import { onShowOnHide } from '../../../lib/mixins.js';
import { paymentError } from '../../../lib/account/payment-errors.js';
import { Amount } from '../../../lib/account/domain.js';
export default {
  components: { MwNetworkSelector, CryptoSendLayout, MwButton, MwButtonGroup, MwFormInput, PasteIcon, QrIcon },
  extends: MwStep, mixins: [onShowOnHide],
  data() { return { address: '', error: '', permissionError: '', recoveryError: '', isLoading: false, recovering: false, isPasteAvailable: !!navigator.clipboard?.readText, isQrScanAvailable: false, generation: 0, active: false }; },
  onShow() {
    this.active = true; this.generation++; this.recovering = false;
    if (this.storage.temp?.address !== undefined) { this.address = this.storage.temp.address; this.storage.temp.address = undefined; }
    if (this.args?.error) this.permissionError = this.args.error.code === 'CAMERA_UNAVAILABLE' ? this.$t('Camera unavailable. Enter the address or tag manually.') : paymentError(this.args.error, this.$t);
    this.recover(); this.cameraAvailable();
  },
  onHide() { this.active = false; this.generation++; },
  methods: {
    async cameraAvailable() {
      const generation = this.generation;
      try { const available = !!navigator.mediaDevices?.getUserMedia && await isQrScanAvailable(); if (this.active && generation === this.generation) this.isQrScanAvailable = available; }
      catch { if (this.active && generation === this.generation) { this.isQrScanAvailable = false; this.permissionError = this.$t('Camera unavailable. Enter the address or tag manually.'); } }
    },
    async recover() {
      if (this.recovering) return;
      const generation = this.generation, wallet = this.$wallet;
      this.recovering = true; this.recoveryError = '';
      try {
        const { intents } = await wallet.pendingPayments();
        if (!this.active || generation !== this.generation) return;
        const intent = intents.find(item => ['preparing','quoted','authorized','submitted','ambiguous','included','reorganized'].includes(item.status));
        if (intent) {
          if (intent.contract.toLowerCase() !== wallet.crypto.address.toLowerCase()) {
            const target = this.$account.wallets().find(item => item.crypto.address.toLowerCase() === intent.contract.toLowerCase());
            if (target) this.$router.replace({ name: 'crypto.send', params: { cryptoId: target.crypto._id } });
            return;
          }
          this.updateStorage({ intent, address: intent.to, alias: intent.recipientTag ? formatMidoriTag(intent.recipientTag) : '', amount: new Amount(intent.amountAtomic, wallet.crypto.decimals) });
          this.replace(intent.status === 'quoted' ? 'confirm' : 'status');
        }
      } catch { if (this.active && generation === this.generation) this.recoveryError = this.$t('The connection was interrupted. Check this payment before trying again.'); }
      finally { this.recovering = false; }
    },
    async confirm() {
      if (this.isLoading || this.recovering || !this.active) return;
      const generation = this.generation, wallet = this.$wallet;
      this.isLoading = true; this.error = '';
      try {
        const recipient = await wallet.resolveRecipient(this.address);
        if (!this.active || generation !== this.generation) return;
        this.updateStorage({ address: recipient.address, alias: recipient.alias || '', recipient, intent: undefined, temp: this.storage.temp });
        this.next('amount');
      } catch (error) { if (this.active && generation === this.generation) this.error = paymentError(error, this.$t, { symbol: wallet.crypto.symbol }); }
      finally { this.isLoading = false; }
    },
    async paste() { try { this.address = await navigator.clipboard.readText(); this.error = ''; this.permissionError = ''; } catch { this.permissionError = this.$t('Could not paste. Enter the address or tag manually.'); } },
  },
};
</script>
<template>
  <CryptoSendLayout class="&" :title="$t('Send {symbol}', { symbol: $wallet.crypto.symbol })" :current="0" :storage="storage" @back="back">
    <MwNetworkSelector :symbol="$wallet.crypto.symbol" :network="$wallet.crypto.platform" destination="crypto.send" />
    <fieldset :disabled="isLoading" class="&__fields"><MwFormInput v-model="address" :label="$t('Wallet address or Midori tag')" placeholder="$usuario / 0x…" :error="error" :clear="true" @update:modelValue="error = ''" /><MwButtonGroup class="&__tools" type="circle"><MwButton v-if="isPasteAvailable" type="circle" @click="paste"><template #circle><PasteIcon /></template>{{ $t('Paste') }}</MwButton><MwButton v-if="isQrScanAvailable" type="circle" @click="next('qr')"><template #circle><QrIcon /></template>{{ $t('Scan QR') }}</MwButton></MwButtonGroup></fieldset>
    <p v-if="permissionError" role="status" class="&__notice">{{ permissionError }}</p>
    <div v-if="recoveryError" role="status" class="&__notice"><p>{{ recoveryError }}</p><MwButton type="primary-link" small :isLoading="recovering" @click="recover">{{ $t('Check payment status') }}</MwButton></div>
    <template #actions><MwButton type="primary" :isLoading="isLoading || recovering" @click="confirm">{{ $t('Continue') }}</MwButton></template>
  </CryptoSendLayout>
</template>
<style lang="scss">
.#{ $filename } { &__fields { display: flex; flex-direction: column; min-width: 0; border: 0; padding: 0; margin: 0; gap: var(--space-lg); } &__tools { max-width: 22rem; width: 100%; align-self: center; } &__notice { color: var(--color-warning); font-size: var(--text-label); line-height: 1.5; } }
</style>
