<script>
import MwListItem from '../../../components/MwListItem.vue';
import MwListItems from '../../../components/MwListItems.vue';

import DeleteIcon from '../../../assets/svg/delete.svg';
import InfoIcon from '../../../assets/svg/info.svg';
import StakingIcon from '../../../assets/svg/staking.svg';

export default {
  components: {
    MwListItems,
    MwListItem,
    InfoIcon,
    DeleteIcon,
    StakingIcon,
  },
  emits: ['remove'],
  data() {
    return {
      recoveryError: '',
      isAccountRecoverySupported: this.$wallet.isAccountRecoverySupported,
      isExportSupported: this.$wallet.isExportSupported,
      isImportSupported: this.$wallet.isImportSupported,
      isSettingsSupported: this.$wallet.isSettingsSupported,
      isStakingSupported: this.$wallet.isStakingSupported,
    };
  },
};
</script>

<template>
  <MwListItems class="&">
    <template v-if="$walletState === $STATE_LOADED || $walletState === $STATE_LOADING">
      <MwListItem
        v-if="$showRampsAndExchangeAndStaking && isStakingSupported"
        :disabled="$walletState === $STATE_LOADING"
        :title="$t('Staking')"
        @click="$router.push({ name: 'crypto.staking', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #before>
          <StakingIcon />
        </template>
      </MwListItem>
<MwListItem :title="$t('Advanced tools')" @click="$router.push({name:'settings.advanced'})" />
      <MwListItem
        v-if="$wallet.crypto.type === 'token'"
        :disabled="$walletState === $STATE_LOADING"
        :title="$t('Token info')"
        @click="$router.push({ name: 'crypto.info', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #before>
          <InfoIcon />
        </template>
      </MwListItem>
    </template>

    <MwListItem
      :title="$t('Remove')"
      type="danger"
      :arrow="false"
      :disabled="$walletState === $STATE_LOADING"
      @click="$emit('remove')"
    >
      <template #before>
        <DeleteIcon />
      </template>
    </MwListItem>
  </MwListItems>
</template>

<style lang="scss">
  .#{ $filename } {
    @include breakpoint(lg) {
      display: none;
    }
  }
</style>
