<script>
import MwStep from '../../../components/MwStep.vue';
import MwTransactionStatus from '../../../components/MwTransactionStatus.vue';
import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: {
    MwTransactionStatus,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  onShow() {
    if (this.storage.status === false) {
      this.$wallet.state = this.$STATE_ERROR;
    }
  },
};
</script>

<template>
  <MwTransactionStatus
    :title="$t('Accelerate')"
    :header="storage.status ? $t('Acceleration successful') : $t('Acceleration failed')"
    :status="storage.status"
    :message="storage.message"
    :action="storage.action"
  />
</template>
