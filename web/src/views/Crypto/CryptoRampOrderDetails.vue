<script>
import MwActivityReceipt from '../../components/MwActivityReceipt.vue';
import { orderRecord } from '../../lib/account/activity-presentation.js';
export default {
  components: { MwActivityReceipt },
  props: { order: { type: Object, default: null }, isLoading: Boolean, error: { type: String, default: '' } },
  emits: ['close', 'refresh'],
  computed: { record() { return this.order ? orderRecord(this.order) : null; } },
};
</script>
<template><MwActivityReceipt :show="!!order" :record="record" :loading="isLoading" :error="error" @close="$emit('close')" @refresh="$emit('refresh')" /></template>
