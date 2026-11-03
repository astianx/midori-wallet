<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoExchangeStepAddress from './CryptoExchangeStepAddress.vue';
import CryptoExchangeStepConfirm from './CryptoExchangeStepConfirm.vue';
import CryptoExchangeStepFilterBlockchain from './CryptoExchangeStepFilterBlockchain.vue';
import CryptoExchangeStepIndex from './CryptoExchangeStepIndex.vue';
import CryptoExchangeStepMecto from './CryptoExchangeStepMecto.vue';
import CryptoExchangeStepMeta from './CryptoExchangeStepMeta.vue';
import CryptoExchangeStepPoor from './CryptoExchangeStepPoor.vue';
import CryptoExchangeStepProvider from './CryptoExchangeStepProvider.vue';
import CryptoExchangeStepQr from './CryptoExchangeStepQr.vue';
import CryptoExchangeStepStatus from './CryptoExchangeStepStatus.vue';
import CryptoExchangeStepTarget from './CryptoExchangeStepTarget.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: CryptoExchangeStepIndex,
    target: CryptoExchangeStepTarget,
    provider: CryptoExchangeStepProvider,
    address: CryptoExchangeStepAddress,
    meta: CryptoExchangeStepMeta,
    confirm: CryptoExchangeStepConfirm,
    status: CryptoExchangeStepStatus,
    mecto: CryptoExchangeStepMecto,
    pin: MwPinStep,
    qr: CryptoExchangeStepQr,
    poor: CryptoExchangeStepPoor,
    filterBlockchain: CryptoExchangeStepFilterBlockchain,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
