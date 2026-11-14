<script>
import CryptoRampPanel from './CryptoRampPanel.vue';
import MainLayout from '../../layouts/MainLayout.vue';

import { cryptoSubtitle } from '../../lib/helpers.js';

export default {
  components: {
    CryptoRampPanel,
    MainLayout,
  },
  data() {
    return {
      subtitle: cryptoSubtitle(this.$wallet),
    };
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Buy {symbol}', { symbol: $wallet.crypto.symbol })"
    :description="subtitle"
  >
    <CryptoRampPanel kind="buy" />
  </MainLayout>
</template>
