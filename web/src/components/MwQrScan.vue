<script>
import MwQrScanPhonegap from './MwQrScanPhonegap.vue';
import MwQrScanWeb from './MwQrScanWeb.vue';
export default {
  components: {
    MwQrScanPhonegap,
    MwQrScanWeb,
  },
  emits: ['back', 'scan'],
};
</script>

<template>
  <component
    :is="'MwQrScanWeb'"
    @back="$emit('back')"
    @scan="(args) => $emit('scan', args)"
  />
</template>
