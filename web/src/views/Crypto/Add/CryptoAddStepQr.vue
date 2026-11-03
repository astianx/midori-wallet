<script>
import MwQrScan from '../../../components/MwQrScan.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { parseCryptoURI } from '../../../lib/cryptoURI.js';

export default {
  components: {
    MwQrScan,
    MainLayout,
  },
  extends: MwStep,
  methods: {
    scan({ data }) {
      try {
        const parsed = parseCryptoURI(data);
        const temp = {
          address: parsed.address,
        };
        this.updateStorage({ temp });
        this.back();
      } catch (error) {
        this.back({ error });
      }
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
