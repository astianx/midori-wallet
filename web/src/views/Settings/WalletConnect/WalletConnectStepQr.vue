<script>
import MwQrScan from '../../../components/MwQrScan.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

export default {
  components: {
    MwQrScan,
    MainLayout,
  },
  extends: MwStep,
  methods: {
    scan({ data }) {
      this.updateStorage({ uri: data });
      this.back();
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Scan QR')"
  >
    <MwQrScan
      @back="back"
      @scan="scan"
    />
  </MainLayout>
</template>
