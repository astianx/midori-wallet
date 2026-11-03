<script>
import QRCode from 'qrcode-svg';
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import CopyIcon from '../../../assets/svg/copy.svg';
import ShareIcon from '../../../assets/svg/share.svg';

export default {
  components: { MainLayout, MwButton, MwButtonGroup, MwFormTextareaReadonly, CopyIcon, ShareIcon },
  extends: MwStep,
  data() { return { receiveMode: 'external', isCopied: false, copyError: false }; },
  computed: {
    tag() { return this.$user.username && this.$account.tag ? `@${this.$user.username}` : ''; },
    receiveValue() { return this.receiveMode === 'midori' ? this.tag : this.$wallet.address; },
    qr() { return this.receiveValue ? new QRCode({ content: this.receiveValue, join: true, padding: 0, container: 'svg-viewbox' }).svg() : ''; },
  },
  methods: {
    async copy() {
      try { await navigator.clipboard.writeText(this.receiveValue); this.isCopied = true; this.copyError = false; setTimeout(() => { this.isCopied = false; }, 1500); }
      catch { this.copyError = true; }
    },
    async share() {
      const text = this.receiveMode === 'midori' ? `${this.$t('Receive with Midori Wallet')}\n${this.tag}` : `${this.$wallet.crypto.symbol} · Base\n${this.receiveValue}`;
      try {
        if (this.env.VITE_BUILD_TYPE === 'phonegap') await window.plugins.socialsharing.shareWithOptions({ message: text });
        else if (navigator.share) await navigator.share({ title: this.$t('Receive'), text });
        else this.$safeOpen(`mailto:?body=${encodeURIComponent(text)}`);
      } catch { /* Sharing can be cancelled. */ }
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Receive {symbol}', { symbol: $wallet.crypto.symbol })" allowReadFailure>
    <MwButtonGroup>
      <MwButton :type="receiveMode === 'midori' ? 'primary' : 'primary-light'" @click="receiveMode = 'midori'; isCopied = false">{{ $t('From Midori Wallet') }}</MwButton>
      <MwButton :type="receiveMode === 'external' ? 'primary' : 'primary-light'" @click="receiveMode = 'external'; isCopied = false">{{ $t('From another wallet or service') }}</MwButton>
    </MwButtonGroup>
    <template v-if="receiveMode === 'external'">
      <p>{{ $t('Send only {symbol} using the Base network.', { symbol: $wallet.crypto.symbol }) }}</p>
    </template>
    <template v-else>
      <p>{{ tag ? $t('Share your tag with the person sending you money.') : $t('Choose a tag to receive from another Midori user.') }}</p>
      <MwButton v-if="!tag" type="primary" @click="$router.push('/settings/account')">{{ $t('Choose your tag') }}</MwButton>
    </template>
    <div v-if="qr" class="&__qr-wrapper">
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="&__qr" v-html="qr" />
    </div>
    <MwFormTextareaReadonly v-if="receiveValue" :value="receiveValue" :label="receiveMode === 'midori' ? $t('Your Midori tag') : $t('Your wallet address')" />
    <MwButtonGroup v-if="receiveValue" class="&__actions" type="circle">
      <MwButton type="circle" @click="copy"><template #circle><CopyIcon /></template>{{ isCopied ? $t('Copied!') : $t('Copy') }}</MwButton>
      <MwButton type="circle" @click="share"><template #circle><ShareIcon /></template>{{ $t('Share') }}</MwButton>
    </MwButtonGroup>
    <p v-if="copyError" role="alert">{{ $t('Could not copy. Select the address and copy it manually.') }}</p>
    <p v-if="receiveMode === 'external' && tag" class="&__hint">{{ $t('In Midori Wallet, you can also receive with {tag}.', { tag }) }}</p>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__qr-wrapper { display: flex; justify-content: center; }
    &__qr { width: 12rem; height: 12rem; padding: var(--spacing-md); border: 1px solid var(--color-neutral); border-radius: 0.625rem; background-color: var(--color-white); }
    &__actions { width: 100%; max-width: 25rem; align-self: center; }
    &__hint { color: var(--color-secondary); }
  }
</style>
