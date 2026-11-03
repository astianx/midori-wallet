<script>
import { cryptoSubtitle } from '../../../lib/helpers.js';

import MwPoor from '../../../components/MwPoor.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwPoor,
  },
  extends: MwStep,
  data() {
    return {
      subtitle: cryptoSubtitle(this.$wallet),
    };
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Stake {symbol}', { symbol: $wallet.crypto.symbol })"
    :description="subtitle"
  >
    <MwPoor />
  </MainLayout>
</template>
