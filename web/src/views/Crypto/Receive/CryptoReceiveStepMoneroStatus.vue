<script>
import MwStep from '../../../components/MwStep.vue';
import MwTransactionStatus from '../../../components/MwTransactionStatus.vue';

import * as MoneroErrors from '../../../lib/ui-runtime/engine-errors.js';

export default {
  components: {
    MwTransactionStatus,
  },
  extends: MwStep,
  computed: {
    message() {
      if (this.storage.status === false) {
        if (this.storage.error instanceof MoneroErrors.NotYourTransactionError) {
          return this.$t("Not your transaction. It can't be accepted.");
        }
      }
    },
    action() {
      if (this.storage.status === false) {
        if (this.storage.error instanceof MoneroErrors.NotYourTransactionError) {
          return this.$t('Back');
        }
      }
    },
  },
};
</script>

<template>
  <MwTransactionStatus
    :title="$t('Accept transaction')"
    :header="storage.status ? $t('Transaction accepted') : $t('Failed')"
    :status="storage.status"
    :message="message"
    :action="action"
  />
</template>
