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
        if (this.storage.error instanceof MoneroErrors.InvalidTransactionIDError) {
          return this.$t('Invalid transaction ID');
        }
        if (this.storage.error instanceof MoneroErrors.TransactionAlreadyAddedError) {
          return this.$t('Transaction has already been accepted');
        }
        if (this.storage.error instanceof MoneroErrors.UnknownTransactionError) {
           
          return this.$t('Transaction not found or has less than 10 confirmations. Please wait 20 minutes and try again.');
        }
        console.error('Midori Wallet operation failed');
        return this.$account.unknownError(this.storage.error);
      }
    },
    action() {
      if (this.storage.status === false) {
        if (this.storage.error instanceof MoneroErrors.NotYourTransactionError
          || this.storage.error instanceof MoneroErrors.InvalidTransactionIDError
          || this.storage.error instanceof MoneroErrors.TransactionAlreadyAddedError) {
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
