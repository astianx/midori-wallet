<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';

import HistoryIcon from '../../../assets/svg/history.svg';
import ReceiveIcon from '../../../assets/svg/receive.svg';
import SendIcon from '../../../assets/svg/send.svg';
import SwapIcon from '../../../assets/svg/swap.svg';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    HistoryIcon,
    ReceiveIcon,
    SendIcon,
    SwapIcon,
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
  <div
    v-if="$wallet.address"
    class="&"
  >
    <MwButtonGroup
      class="&__group"
      type="circle"
    >
      <MwButton
        type="circle"
        :disabled="!$account.capabilities.payments"
        @click="$router.push({ name: 'crypto.send', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #circle>
          <SendIcon />
        </template>
        {{ $t('Send') }}
      </MwButton>
      <MwButton
        type="circle"
        :disabled="!$wallet.address"
        @click="$router.push({ name: 'crypto.receive', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #circle>
          <ReceiveIcon />
        </template>
        {{ $t('Receive') }}
      </MwButton>
      <MwButton
        v-if="$showRampsAndExchangeAndStaking"
        type="circle"
        :disabled="disabled"
        @click="$router.push({ name: 'crypto.swap', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #circle>
          <SwapIcon />
        </template>
        {{ $t('Swap') }}
      </MwButton>
      <MwButton
        type="circle"
        :disabled="!$wallet.address"
        @click="$router.push({ name: 'crypto.history', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #circle>
          <HistoryIcon />
        </template>
        {{ $t('History') }}
      </MwButton>
    </MwButtonGroup>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    flex-grow: 1;

    &__group {
      max-width: 25rem;
    }
  }
</style>
