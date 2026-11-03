<script>
import MwMinerFee from '../../../components/MwMinerFee.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { cryptoSubtitle } from '../../../lib/helpers.js';

export default {
  components: {
    MainLayout,
    MwMinerFee,
  },
  extends: MwStep,
  data() {
    return {
      subtitle: cryptoSubtitle(this.$wallet),
    };
  },
  methods: {
    confirm(feeRate) {
      this.updateStorage({ feeRate });
      this.next('amount');
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Send {symbol}', { symbol: $wallet.crypto.symbol })"
    :description="subtitle"
  >
    <MwMinerFee
      :feeRates="$wallet.feeRates"
      @confirm="confirm"
    />
  </MainLayout>
</template>
