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
  methods: {
    done() {
      this.$router.replace({ name: 'crypto.staking', params: { cryptoId: this.$wallet.crypto._id }, force: true });
    },
  },
};
</script>

<template>
  <MwTransactionStatus
    :title="storage.title"
    :status="storage.status"
    @done="done"
  />
</template>
