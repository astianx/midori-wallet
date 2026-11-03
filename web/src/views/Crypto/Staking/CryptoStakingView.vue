<script>
import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoStakingStepConfirm from './CryptoStakingStepConfirm.vue';
import CryptoStakingStepIndex from './CryptoStakingStepIndex.vue';
import CryptoStakingStepPendingRequests from './CryptoStakingStepPendingRequests.vue';
import CryptoStakingStepPoor from './CryptoStakingStepPoor.vue';
import CryptoStakingStepStake from './CryptoStakingStepStake.vue';
import CryptoStakingStepStatus from './CryptoStakingStepStatus.vue';
import CryptoStakingStepUnstake from './CryptoStakingStepUnstake.vue';

export default {
  components: {
    MwSteps,
  },
  steps: {
    index: CryptoStakingStepIndex,
    stake: CryptoStakingStepStake,
    unstake: CryptoStakingStepUnstake,
    pendingRequests: CryptoStakingStepPendingRequests,
    confirm: CryptoStakingStepConfirm,
    status: CryptoStakingStepStatus,
    poor: CryptoStakingStepPoor,
    pin: MwPinStep,
  },
};
</script>

<template>
  <MwSteps
    :steps="$options.steps"
  />
</template>
