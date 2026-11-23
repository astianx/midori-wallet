<script>
import BlockchainIcon from '../assets/svg/blockchain.svg';
import TokenIcon from '../assets/svg/token.svg';

export default {
  components: {
    BlockchainIcon,
    TokenIcon,
  },
  props: {
    crypto: {
      type: Object,
      required: true,
    },
    platform: {
      type: Object,
      default: undefined,
    },
  },
  data() {
    return {
      baseUrl: this.$account.getBaseURL('price'),
      version: this.env.VITE_VERSION,
      cryptoLogoError: false,
      platformLogoError: false,
    };
  },
};
</script>

<template>
  <div class="&" :class="{ '&--stable': ['USDC', 'USDT', 'USDT0'].includes(crypto.symbol) }">
    <svg v-if="['USDC', 'USDT', 'USDT0'].includes(crypto.symbol)" class="&__stable" :class="`&__stable--${crypto.symbol.toLowerCase().replace('usdt0', 'usdt')}`" viewBox="0 0 48 48" role="img" :aria-label="crypto.symbol">
      <circle cx="24" cy="24" r="24" fill="currentColor" />
      <g v-if="crypto.symbol === 'USDC'" class="&__coin-mark" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M19 17c2-3 11-2 11 2m-12 9c0 4 12 5 12-1 0-5-12-2-12-7 0-5 12-5 12 0M24 12v24M13 15a15 15 0 0 0 0 18m22-18a15 15 0 0 1 0 18" /></g>
      <g v-else class="&__coin-mark" fill="currentColor"><path d="M13 12h22v5h-8v4c7 .3 11 1 11 2.5S33 26 27 26v12h-6V26c-6-.1-11-1-11-2.5s4-2.2 11-2.5v-4h-8Zm8 10c-5 .2-8 .7-8 1.5S17 25 24 25s11-.6 11-1.5-3-1.3-8-1.5v2h-6Z" /></g>
    </svg>
    <svg v-else-if="crypto.symbol === 'BTC'" class="&__crypto" viewBox="0 0 48 48" role="img" aria-label="Bitcoin"><circle cx="24" cy="24" r="24" fill="#f7931a"/><path fill="#fff" d="m34 21-1 3c-1 2-3 3-6 3l-1 4-3-1 1-4-2-.5-1 4-3-1 1-4-5-1 1-3 2 .5 3-11-2-.5 1-3 5 1 1-4 3 1-1 4 2 .5 1-4 3 1-1 4c4 2 5 4 3 7-1 1-2 2-4 2 3 1 4 2 4 4Zm-9-10-1 5 3 1c4 1 6-4 1-5Zm-2 8-1 5 4 1c5 1 7-4 1-5Z"/></svg>
    <img
      v-else-if="crypto.logo && !cryptoLogoError"
      loading="lazy"
      class="&__crypto"
      :src="`${baseUrl}logo${$resolvedTheme === 'dark' ? '-dark' : ''}/${crypto.logo}?ver=${version}`"
      :alt="crypto.name"
      @error="cryptoLogoError = true"
    >
    <BlockchainIcon
      v-else-if="crypto.type === 'custom'"
      class="&__crypto"
    />
    <TokenIcon
      v-else
      class="&__crypto"
    />
    <template v-if="crypto.type === 'token' && !['USDC', 'USDT', 'USDT0'].includes(crypto.symbol)">
      <img
        v-if="platform?.logo && !platformLogoError"
        loading="lazy"
        class="&__platform"
        :src="`${baseUrl}logo${$resolvedTheme === 'dark' ? '-dark' : ''}/${platform.logo}?ver=${version}`"
        :alt="platform.name"
        @error="platformLogoError = true"
      >
      <TokenIcon
        v-else
        class="&__platform"
      />
    </template>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    position: relative;
    width: 100%;
    height: 100%;

    &__stable { width: 100%; height: 100%; }
    &__stable--usdc { color: var(--color-usdc); }
    &__stable--usdt { color: var(--color-usdt); }
    &__coin-mark { color: var(--color-coin-ink); }
    &__crypto {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }

    &__platform {
      position: absolute;
      right: -12.5%;
      bottom: -12.5%;
      width: 50%;
      height: 50%;
      border-radius: 50%;
      box-shadow: 0 0 0 1px var(--color-background);
    }
  }
</style>
