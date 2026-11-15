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
        const parsed = parseCryptoURI(data);
        if (parsed.token && parsed.token !== this.$wallet.crypto.address.toLowerCase()) {
          const target = this.$account.wallets().find(wallet => wallet.crypto.address.toLowerCase() === parsed.token);
          if (!target) throw new Error('Unsupported payment asset');
          this.$router.replace({ name: 'crypto.send', params: { cryptoId: target.crypto._id }, query: { address: parsed.address, ...(parsed.amount ? { amount: parsed.amount } : {}) } });
          return;
        }
        const temp = {
          address: parsed.address,
        };
        if (parsed.amount) {
          try {
            temp.amount = Amount.fromString(parsed.amount, this.$wallet.crypto.decimals);
          } catch (err) {
            console.error('Midori Wallet operation failed');
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
        this.back({ error: { code: 'INVALID_RECIPIENT' } });
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
      @back="back({ error: { code: 'CAMERA_UNAVAILABLE' } })"
      @scan="scan"
    />
  </MainLayout>
</template>
