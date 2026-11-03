<script>
import MwSteps from '../../../components/MwSteps.vue';

import CryptoImportStepConfirm from './CryptoImportStepConfirm.vue';
import CryptoImportStepMinerFee from './CryptoImportStepMinerFee.vue';
import CryptoImportStepPrivateKey from './CryptoImportStepPrivateKey.vue';
import CryptoImportStepQr from './CryptoImportStepQr.vue';
import CryptoImportStepStatus from './CryptoImportStepStatus.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: CryptoImportStepMinerFee,
    privateKey: CryptoImportStepPrivateKey,
    confirm: CryptoImportStepConfirm,
    status: CryptoImportStepStatus,
    qr: CryptoImportStepQr,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
