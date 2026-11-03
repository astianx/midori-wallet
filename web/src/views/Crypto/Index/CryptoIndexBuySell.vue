<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';

export default {
  components: {
    MwButton,
    MwButtonGroup,
  },
  computed: {
    disabled() {
      if (this.$wallet.crypto._id === 'eos@eos') return true;
      return this.$walletState === this.$STATE_LOADING;
    },
  },
};
</script>

<template>
  <MwButtonGroup
    v-if="$showRampsAndExchangeAndStaking"
    type="horizontal"
  >
    <MwButton
      v-if="$walletState === $STATE_LOADED || $walletState === $STATE_LOADING"
      type="primary-light"
      small
      :disabled="disabled"
      @click="$router.push({ name: 'crypto.buy', params: { cryptoId: $wallet.crypto._id }})"
    >
      {{ $t('Buy') }}
    </MwButton>
    <MwButton
      v-if="$walletState === $STATE_LOADED || $walletState === $STATE_LOADING"
      type="danger-light"
      small
      :disabled="disabled"
      @click="$router.push({ name: 'crypto.sell', params: { cryptoId: $wallet.crypto._id }})"
    >
      {{ $t('Sell') }}
    </MwButton>
  </MwButtonGroup>
</template>
