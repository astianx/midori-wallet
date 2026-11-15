<script>
import CryptoRampPanel from './CryptoRampPanel.vue';
import MainLayout from '../../layouts/MainLayout.vue';



export default {
  components: {
    CryptoRampPanel,
    MainLayout,
  },
  methods: {
    back() {
      const previous = this.$router.options.history.state.back;
      if (previous && this.$router.resolve(previous).name === 'withdraw' && this.$router.resolve(previous).query.method === 'sell') this.$router.back();
      else this.$router.replace({ name: 'withdraw', query: { method: 'sell' } });
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Withdraw')"
    :description="`${$wallet.crypto.symbol} · Transak`"
    :onBack="back"
    wide
    allowReadFailure
  >
    <CryptoRampPanel kind="sell" />
  </MainLayout>
</template>
