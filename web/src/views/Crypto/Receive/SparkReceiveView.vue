<script>
import MwNetworkSelector from '../../../components/MwNetworkSelector.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import MwButton from '../../../components/MwButton.vue';
import MwFormAmountInput from '../../../components/MwForm/MwFormAmountInput.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import QRCode from 'qrcode-svg';
import { paymentError } from '../../../lib/account/payment-errors.js';
export default {
  components: { MwNetworkSelector, MainLayout, MwButton, MwFormAmountInput, MwFormInput, MwFormTextareaReadonly },
  data() { return { mode: 'spark', amount: undefined, memo: '', invoice: null, deposit: '', deposits: [], busy: false, error: '', copied: false, disposed: false, timer: null, now: Date.now(), revision: 0 }; },
  computed: {
    status() { return this.$t(this.invoice?.status === 'TRANSFER_COMPLETED' ? 'Completed' : this.expired ? 'Expired' : /FAILED/.test(this.invoice?.status || '') ? 'Not completed' : 'Pending'); },
    value() { return this.mode === 'spark' ? this.$wallet.address : this.mode === 'bitcoin' ? this.deposit : this.invoice?.invoice || ''; },
    expired() { return this.invoice && Date.parse(this.invoice.expiresAt) <= this.now; },
    qr() { return this.value && !(this.mode === 'lightning' && this.expired) ? new QRCode({ content: this.value, join: true, padding: 4, container: 'svg-viewbox' }).svg() : ''; },
  },
  async mounted() { this.timer = setInterval(() => { this.now = Date.now(); }, 1000); try { this.invoice = (await this.$wallet.lightningInvoices())[0] || null; } catch { /* No cached invoice is required to receive on Spark. */ } },
  beforeUnmount() { this.disposed = true; this.revision++; clearInterval(this.timer); },
  methods: {
    async choose() { this.revision++; this.copied = false; this.error = ''; if (this.mode === 'bitcoin') await this.loadDeposit(); },
    async loadDeposit() {
      this.busy = true; this.error = '';
      try { const deposit = await this.$wallet.sparkRead('deposit'); const result = await this.$wallet.sparkRead('utxos', { depositAddress: deposit, excludeClaimed: true }); if (!this.disposed) { this.deposit = deposit; this.deposits = [...new Set(result.utxos.map(item => item.txid))]; } }
      catch (error) { if (!this.disposed) this.error = paymentError(error, this.$t); }
      finally { if (!this.disposed) this.busy = false; }
    },
    async create() {
      if (this.busy || !this.amount || this.amount.value <= 0n) return;
      const revision = this.revision; this.busy = true; this.error = '';
      try { const invoice = await this.$wallet.createLightningInvoice(String(this.amount.value), this.memo); if (!this.disposed && revision === this.revision) this.invoice = invoice; }
      catch (error) { if (!this.disposed) this.error = paymentError(error, this.$t); }
      finally { if (!this.disposed) this.busy = false; }
    },
    async copy() { try { await navigator.clipboard.writeText(this.value); if (!this.disposed) this.copied = true; } catch { this.error = this.$t('Could not copy. Select the address and copy it manually.'); } },
    async checkInvoice() { this.busy = true; this.error = ''; try { const result = await this.$wallet.sparkRead('invoiceStatus', this.invoice.id); if (!this.disposed) this.invoice = { ...this.invoice, status: result.status }; } catch (error) { if (!this.disposed) this.error = paymentError(error, this.$t); } finally { if (!this.disposed) this.busy = false; } },
  },
};
</script>
<template>
  <MainLayout class="&" :title="$t('Receive {symbol}', { symbol: 'BTC' })" allowReadFailure>
    <h1>{{ $t('Receive {symbol}', { symbol: 'BTC' }) }}</h1>
    <MwNetworkSelector symbol="BTC" network="spark" destination="crypto.receive" />
    <label>{{ $t('Receive') }}<select v-model="mode" :disabled="busy" @change="choose"><option value="spark">Spark</option><option value="lightning">Lightning</option><option value="bitcoin">Bitcoin → Spark</option></select></label>
    <p v-if="error" role="alert">{{ error }}</p>
    <form v-if="mode === 'lightning'" @submit.prevent="create"><MwFormAmountInput v-model="amount" :label="$t('Amount')" symbol="BTC" :decimals="8" :disabled="busy" /><MwFormInput v-model="memo" :label="$t('Memo')" maxlength="140" :disabled="busy" /><MwButton type="primary" :disabled="!amount || amount.value <= 0n" :isLoading="busy" @click="create">{{ $t('Receive') }} · Lightning</MwButton></form>
    <template v-if="value"><p>{{ $t('Network') }} · {{ mode === 'spark' ? 'Spark' : mode === 'lightning' ? 'Lightning · BOLT11' : 'Bitcoin → Spark' }}</p><div v-if="qr" class="&__qr" role="img" :aria-label="mode"><!-- eslint-disable-next-line vue/no-v-html --><div v-html="qr" /></div><MwFormTextareaReadonly :value="value" :label="mode === 'lightning' ? 'Lightning · BOLT11' : $t('Your wallet address')" /><MwButton type="secondary" :disabled="mode === 'lightning' && expired" @click="copy">{{ $t(copied ? 'Copied!' : 'Copy') }}</MwButton></template>
    <template v-if="mode === 'lightning' && invoice"><p>{{ $t('Expiry date') }} · {{ new Date(invoice.expiresAt).toLocaleString() }} <span v-if="expired">· {{ $t('Expired') }}</span></p><p role="status">{{ status }}</p><MwButton type="secondary" :isLoading="busy" @click="checkInvoice">{{ $t('Check payment status') }}</MwButton></template>
    <template v-if="mode === 'bitcoin'"><p>{{ $t('Bitcoin deposits are credited to Spark after confirmation and fee review.') }}</p><MwButton type="secondary" :isLoading="busy" @click="loadDeposit">{{ $t('Refresh') }}</MwButton><RouterLink v-for="id in deposits" :key="id" :to="{ name: 'crypto.send', params: $route.params, query: { claim: id } }">{{ $t('Review payment') }} · {{ id }}</RouterLink></template>
  </MainLayout>
</template>
<style lang="scss">
.#{ $filename } {
  min-width: 0; h1 { font-size: var(--text-title); font-weight: 600; }
  form { display: flex; flex-direction: column; gap: var(--space-md); }
  select { display: block; width: 100%; min-height: 3rem; color: var(--color-ink); background: var(--color-surface); border: 1px solid var(--color-rule); border-radius: var(--radius-control); padding: var(--space-sm); }
  p, label { font-size: var(--text-label); color: var(--color-muted); line-height: 1.5; } p[role='alert'] { color: var(--color-error); }
  a { overflow-wrap: anywhere; } &__qr { width: min(18rem, 100%); align-self: center; padding: var(--space-sm); background: var(--color-white); border-radius: var(--radius-control); } &__qr svg { width: 100%; height: auto; display: block; }
}
</style>
