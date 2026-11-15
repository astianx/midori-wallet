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
      if (previous && this.$router.resolve(previous).name === 'add-money' && this.$router.resolve(previous).query.method === 'buy') this.$router.back();
      else this.$router.replace({ name: 'add-money', query: { method: 'buy' } });
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Add money')"
    :description="$wallet.crypto.symbol"
    :onBack="back"
    wide
    allowReadFailure
  >
    <CryptoRampPanel kind="buy" />
  </MainLayout>
</template>
