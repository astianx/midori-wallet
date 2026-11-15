<script>
import { formatMidoriTag } from '../../../lib/tags.js';
import { selectReceivingText, shareReceivingText } from '../../../lib/receive-actions.js';
import { onShowOnHide } from '../../../lib/mixins.js';
import QRCode from 'qrcode-svg';
import CopyIcon from '../../../assets/svg/copy.svg';
import MwButton from '../../../components/MwButton.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwCryptoLogo from '../../../components/MwCryptoLogo.vue';
import MwAppIcon from '../../../components/MwAppIcon.vue';
import MwModal from '../../../components/MwModal.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
export default {
  components: { CopyIcon, MainLayout, MwButton, MwFormTextareaReadonly, MwCryptoLogo, MwAppIcon, MwModal },
  extends: MwStep, mixins: [onShowOnHide],
  data() { return { tag: '', tagKnown: false, tagsEnabled: false, paymentsEnabled: false, online: true, active: false, generation: 0, isCopying: false, isCopied: false, copyError: false, selected: false, isSharing: false, shareError: false, showShare: false, shareText: '', timer: null }; },
  computed: {
    midori() { return this.$route.query.source === 'midori'; },
    receiveValue() { return this.midori ? this.tag : this.$wallet.address || ''; },
    qr() { return this.receiveValue ? new QRCode({ content: this.receiveValue, join: true, padding: 4, container: 'svg-viewbox' }).svg() : ''; },
    balanceFailed() { return ['unavailable','stale'].includes(this.$cryptos.find(item => item.crypto._id === this.$wallet.crypto._id)?.readState); },
    valueLabel() { return this.$t(this.midori ? 'Your Midori tag' : 'Your wallet address'); },
    canUseValue() { return !!this.receiveValue && (!this.midori || this.tagsEnabled); },
  },
  onShow() { this.active = true; this.generation++; this.sync(); this.$account.on('update', this.sync); window.addEventListener('online', this.sync); window.addEventListener('offline', this.sync); this.$nextTick(() => { const target = this.args?.saved ? this.$refs.value?.$el.querySelector('[role="textbox"]') : this.$refs.tagButton?.$el || this.$el.querySelector('[aria-pressed="true"]'); target?.focus({ preventScroll: true }); }); },
  onHide() { this.active = false; this.generation++; clearTimeout(this.timer); this.$account.off('update', this.sync); window.removeEventListener('online', this.sync); window.removeEventListener('offline', this.sync); this.showShare = false; },
  methods: {
    sync() { this.tag = this.$account.tag?.alias ? formatMidoriTag(this.$account.tag.alias) : ''; this.tagKnown = this.$account.tag !== undefined; this.tagsEnabled = this.$account.capabilities.tags === true; this.paymentsEnabled = this.$account.capabilities.payments === true; this.online = navigator.onLine !== false && this.$account.connection === 'online'; },
    async choose(source) { if (this.midori === (source === 'midori')) return; await this.$router.replace({ name: 'crypto.receive', params: this.$route.params, query: { ...this.$route.query, source } }); },
    async copy(value = this.receiveValue) {
      if (!value || this.isCopying) return;
      const generation = this.generation; this.isCopying = true; this.copyError = false; this.isCopied = false; this.selected = false; clearTimeout(this.timer);
      try { if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable'); await navigator.clipboard.writeText(value); if (!this.active || generation !== this.generation) return; this.isCopied = true; this.timer = setTimeout(() => { this.isCopied = false; }, 2000); }
      catch { if (this.active && generation === this.generation) this.copyError = true; }
      finally { if (generation === this.generation) this.isCopying = false; }
    },
    select(shared = false) { this.selected = selectReceivingText(shared ? this.$refs.shareValue : this.$refs.value?.$el.querySelector('[role="textbox"]')); },
    async share() {
      if (!this.canUseValue || this.isSharing) return;
      const generation = this.generation;
      this.shareText = this.midori ? `${this.$t('Receive with Midori Wallet')}\n${this.receiveValue}\n${this.$wallet.crypto.symbol} · Base` : `${this.$wallet.crypto.symbol} · Base\n${this.receiveValue}`;
      this.isSharing = true; this.shareError = false; this.copyError = false; this.selected = false; this.isCopied = false;
      try { const result = await shareReceivingText(this.shareText, this.$t('Receive'), this.env.VITE_BUILD_TYPE); if (this.active && generation === this.generation && result === 'fallback') this.showShare = true; }
      catch { if (this.active && generation === this.generation) { this.shareError = true; this.showShare = true; } }
      finally { if (generation === this.generation) this.isSharing = false; }
    },
  },
};
</script>
<template>
  <MainLayout class="&" :title="$t('Receive {symbol}', { symbol: $wallet.crypto.symbol })" wide allowReadFailure>
    <div class="&__origin" role="group" :aria-label="$t('Receive')"><button type="button" :aria-pressed="midori" @click="choose('midori')"><MwAppIcon name="user" /><span>{{ $t('From Midori Wallet') }}</span></button><button type="button" :aria-pressed="!midori" @click="choose('external')"><MwAppIcon name="receive" /><span>{{ $t('From another wallet or service') }}</span></button></div>
    <div class="&__grid">
      <section class="&__instructions"><div class="&__asset"><MwCryptoLogo :crypto="$wallet.crypto" :platform="$wallet.platform" /><div><h1>{{ $wallet.crypto.symbol }}</h1><span>{{ $wallet.crypto.name }} · Base</span></div></div>
        <p class="&__lead">{{ $t(midori ? tag ? 'Share your tag with the person sending you money.' : 'Choose a tag to receive from another Midori user.' : 'Send only {symbol} using the Base network.', { symbol: $wallet.crypto.symbol }) }}</p>
        <p v-if="!midori" class="&__note">{{ $t('USDT and USDC on Base share your account address.') }}</p>
        <p v-if="!online || balanceFailed" class="&__notice" role="status">{{ $t('Receiving details remain available. Check Activity when connected.') }}</p>
        <p v-if="midori && online && !tagsEnabled" class="&__notice" role="status">{{ $t('Tags are unavailable. Use your Base address.') }}</p>
        <p v-else-if="midori && !paymentsEnabled && tagsEnabled" class="&__notice" role="status">{{ $t('Tag payments will be available when sending is enabled. You can receive now using your Base address.') }}</p>
        <template v-if="midori && !tag"><p v-if="!online || !tagKnown" class="&__note">{{ $t('Connect to save your tag. Your Base address is still available.') }}</p><MwButton ref="tagButton" v-else-if="tagsEnabled" type="primary" @click="next('account')">{{ $t('Choose your tag') }}</MwButton></template>
        <MwButton v-if="midori && (!canUseValue || !online || !paymentsEnabled)" type="primary-link" @click="choose('external')">{{ $t('From another wallet or service') }}</MwButton>

      </section>
      <section v-if="receiveValue" class="&__identity" :aria-label="valueLabel"><div class="&__qr-heading"><strong>{{ valueLabel }}</strong><small>{{ midori ? $t('Midori Wallet') : `${$wallet.crypto.symbol} · Base` }}</small></div><div class="&__qr" role="img" :aria-label="`${valueLabel}: ${receiveValue}`"><!-- eslint-disable-next-line vue/no-v-html --><div v-html="qr" /></div><MwFormTextareaReadonly ref="value" :value="receiveValue" :label="valueLabel" />
        <div class="&__actions"><MwButton type="primary" :disabled="!canUseValue" :isLoading="isCopying" @click="copy()"><MwAppIcon v-if="isCopied" name="check" /><CopyIcon v-else />{{ $t(isCopied ? 'Copied!' : 'Copy') }}</MwButton><MwButton type="secondary" :disabled="!canUseValue" :isLoading="isSharing" @click="share">{{ $t('Share') }}</MwButton></div><MwButton class="&__select" type="primary-link" small @click="select()">{{ $t('Select text') }}</MwButton>
      </section>
      <aside class="&__more"><details v-if="!midori" class="&__token"><summary>{{ $t('Token info') }}</summary><p>{{ $wallet.crypto.representation }}</p><strong>{{ $t('Token contract on Base') }}</strong><code dir="ltr">{{ $wallet.crypto.address }}</code><p>{{ $t('This contract identifies the token. Do not send funds to it.') }}</p></details>
        <RouterLink class="&__activity" :to="{ name: 'activity' }">{{ $t('Activity') }}<MwAppIcon name="arrow" /></RouterLink></aside>
    </div>
    <p v-if="args?.saved && tag" role="status" class="&__feedback">{{ $t('Your Midori tag') }}: {{ tag }}</p><p v-if="copyError" role="alert" class="&__error">{{ $t('Could not copy. Select the address and copy it manually.') }}</p><p class="&__feedback" role="status" aria-live="polite">{{ isCopied ? $t('Copied!') : selected ? $t('Text selected. Copy it manually.') : '' }}</p>
    <MwModal :show="showShare" :title="$t('Share')" @close="showShare = false"><p v-if="shareError" role="alert" class="&__error">{{ $t('Could not share. Copy or select the details instead.') }}</p><textarea ref="shareValue" readonly class="&__share-text" :aria-label="$t('Receive')" :value="shareText" /><p v-if="copyError" role="alert">{{ $t('Could not copy. Select the address and copy it manually.') }}</p><p role="status" aria-live="polite">{{ isCopied ? $t('Copied!') : selected ? $t('Text selected. Copy it manually.') : '' }}</p><template #footer><div class="&__actions"><MwButton type="primary" :isLoading="isCopying" @click="copy(shareText)">{{ $t('Copy') }}</MwButton><MwButton type="secondary" @click="select(true)">{{ $t('Select text') }}</MwButton></div></template></MwModal>
  </MainLayout>
</template>
<style lang="scss">
/* Hallmark · design-system: design.md · Workbench · pre-emit critique: P5 H5 E4 S5 R5 V4 */
.#{ $filename } {
  &__origin { display: flex; flex-direction: column; gap: var(--space-xs); border-bottom: 1px solid var(--color-rule); padding-bottom: var(--space-md); }
  &__origin button { display: flex; align-items: center; gap: var(--space-sm); min-height: 3rem; padding: var(--space-sm); border-radius: var(--radius-control); text-align: start; font-size: var(--text-label); color: var(--color-muted); }
  &__origin svg { width: 1.25rem; height: 1.25rem; flex: none; }
  &__origin button[aria-pressed='true'] { background: var(--color-surface-tint); color: var(--color-accent); font-weight: 600; }
  &__origin button:hover { background: var(--color-surface-subtle); }
  &__grid { display: grid; grid-template-columns: minmax(0,1fr); gap: var(--space-lg); align-content: start; }
  &__instructions, &__identity, &__more { min-width: 0; }
  &__asset { display: flex; align-items: center; gap: var(--space-sm); }
  &__asset .mw-crypto-logo { width: 2.75rem; height: 2.75rem; flex: none; }
  &__asset h1 { margin: 0; font-size: 1.375rem; font-weight: 600; }
  &__asset span, &__note { color: var(--color-muted); font-size: var(--text-label); line-height: 1.5; }
  &__lead { font-size: 1rem; line-height: 1.5; margin-block: var(--space-lg) var(--space-sm); }
  &__notice { color: var(--color-warning); font-size: var(--text-label); line-height: 1.5; }
  &__identity { display: flex; flex-direction: column; gap: var(--space-md); }
  &__qr-heading { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-sm); font-size: var(--text-label); }
  &__qr-heading small { color: var(--color-muted); }
  &__qr { width: min(14rem,100%); padding: var(--space-md); align-self: center; background: var(--color-white); border-radius: var(--radius-control); }
  &__qr svg { display: block; width: 100%; height: auto; }
  &__actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); }
  &__actions > button { flex: 1 0 auto; }
  &__actions svg { width: 1.25rem; height: 1.25rem; }
  &__select { align-self: center; }
  &__token { border-top: 1px solid var(--color-rule); margin-top: var(--space-lg); font-size: var(--text-label); }
  &__token summary, &__activity { min-height: 2.75rem; display: flex; align-items: center; color: var(--color-accent); cursor: pointer; }
  &__token code { display: block; margin-top: var(--space-xs); overflow-wrap: anywhere; font-family: var(--font-mono); }
  &__token p { color: var(--color-muted); line-height: 1.5; }
  &__activity { gap: var(--space-sm); margin-top: var(--space-md); font-size: var(--text-label); }
  &__activity svg { width: 1.125rem; height: 1.125rem; }
  &__feedback { margin: 0; min-height: 1.25rem; color: var(--color-accent); font-size: var(--text-label); }
  &__error { color: var(--color-error); font-size: var(--text-label); }
  &__share-text { width: 100%; min-height: 7rem; resize: vertical; color: var(--color-ink); background: var(--color-surface-subtle); border: 1px solid var(--color-rule); border-radius: var(--radius-control); padding: var(--space-md); font-size: 1rem; line-height: 1.5; overflow-wrap: anywhere; }
  @media (min-width: 48rem) { &__origin { flex-direction: row; } &__origin button { flex: 1; } }
  @media (min-width: 64rem) { &__grid { grid-template-columns: minmax(0,1fr) minmax(0,1fr); grid-template-areas: 'instructions identity' 'more identity'; grid-template-rows: auto 1fr; gap: var(--space-2xl); } &__instructions { grid-area: instructions; } &__more { grid-area: more; } &__identity { grid-area: identity; border-inline-start: 1px solid var(--color-rule); padding-inline-start: var(--space-xl); } }
  .app-layout[data-runtime='phonegap'] &__grid { grid-template-columns: minmax(0,1fr); grid-template-areas: none; grid-template-rows: auto; }
  .app-layout[data-runtime='phonegap'] &__identity, .app-layout[data-runtime='phonegap'] &__instructions, .app-layout[data-runtime='phonegap'] &__more { grid-area: auto; }
  .app-layout[data-runtime='phonegap'] &__identity { border: 0; padding-inline-start: 0; }
}
</style>
