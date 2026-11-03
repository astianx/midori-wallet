<script>
import MwButton from './MwButton.vue';
import MwButtonGroup from './MwButtonGroup.vue';

import BuyIcon from '../assets/svg/buy.svg';
import ReceiveIcon from '../assets/svg/receive.svg';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    BuyIcon,
    ReceiveIcon,
  },
};
</script>

<template>
  <div class="&__content">
    {{ $t('No funds. Buy or receive {symbol} to continue.', { symbol: $wallet.crypto.symbol }) }}
  </div>
  <MwButtonGroup
    class="&__actions"
    type="circle"
  >
    <MwButton
      type="circle"
      @click="$router.push({ name: 'crypto.buy', params: { cryptoId: $wallet.crypto._id }})"
    >
      <template #circle>
        <BuyIcon />
      </template>
      {{ $t('Buy') }}
    </MwButton>
    <MwButton
      type="circle"
      @click="$router.push({ name: 'crypto.receive', params: { cryptoId: $wallet.crypto._id }})"
    >
      <template #circle>
        <ReceiveIcon />
      </template>
      {{ $t('Receive') }}
    </MwButton>
  </MwButtonGroup>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__content {
      flex-grow: 1;
    }

    &__actions {
      width: 100%;
      max-width: 25rem;
      align-self: center;
    }
  }
</style>
