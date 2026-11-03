<script>
import { Amount } from '../../../lib/account/domain.js';

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
        const parsed = data.trim().startsWith('@') ? { address: data.trim() } : parseCryptoURI(data);
        const temp = {
          address: parsed.address,
        };
        if (parsed.amount) {
          try {
            temp.amount = Amount.fromString(parsed.amount, this.$wallet.crypto.decimals);
          } catch (err) {
            console.error(err);
          }
        }
        if (parsed.destinationTag && this.$wallet.crypto._id === 'xrp@ripple') {
          temp.meta = {
            destinationTag: parsed.destinationTag,
          };
        }
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
