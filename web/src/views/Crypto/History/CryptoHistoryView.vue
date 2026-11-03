<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoHistoryStepAccelerate from './CryptoHistoryStepAccelerate.vue';
import CryptoHistoryStepAccelerateStatus from './CryptoHistoryStepAccelerateStatus.vue';
import CryptoHistoryStepList from './CryptoHistoryStepList.vue';
import CryptoHistoryStepMoneroAccept from './CryptoHistoryStepMoneroAccept.vue';
import CryptoHistoryStepMoneroStatus from './CryptoHistoryStepMoneroStatus.vue';
import CryptoHistoryStepTransaction from './CryptoHistoryStepTransaction.vue';

export default {
  components: {
    MwSteps,
  },
  async mounted() {
    if (this.$route.query.payment && /^[0-9a-f-]{36}$/.test(this.$route.query.payment)) {
      try { const transaction = await this.$wallet.loadTransaction(`p${this.$route.query.payment}`); this.$refs.steps.next('transaction', { transaction }); }
      catch { /* The list remains available if a detail cannot be loaded. */ }
    }
  },
  steps: {
    index: CryptoHistoryStepList,
    accelerate: CryptoHistoryStepAccelerate,
    accelerateStatus: CryptoHistoryStepAccelerateStatus,
    transaction: CryptoHistoryStepTransaction,
    moneroAccept: CryptoHistoryStepMoneroAccept,
    moneroStatus: CryptoHistoryStepMoneroStatus,
    pin: MwPinStep,
  },
};
</script>

<template>
  <MwSteps
    ref="steps"
    :steps="$options.steps"
  />
</template>
