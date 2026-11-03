<script>
import { saveRecoveryFile } from '../../../lib/storage/RecoveryFiles.js';
import MwListItem from '../../../components/MwListItem.vue';
import MwListItems from '../../../components/MwListItems.vue';

import DeleteIcon from '../../../assets/svg/delete.svg';
import DerivationIcon from '../../../assets/svg/derivation.svg';
import ExportIcon from '../../../assets/svg/export.svg';
import ImportIcon from '../../../assets/svg/import.svg';
import InfoIcon from '../../../assets/svg/info.svg';
import StakingIcon from '../../../assets/svg/staking.svg';

export default {
  components: {
    MwListItems,
    MwListItem,
    ImportIcon,
    ExportIcon,
    DerivationIcon,
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
  methods: {
    async saveRecovery() {
      try { await saveRecoveryFile(this.$account.recoveryDescriptor); this.recoveryError = ''; }
      catch { this.recoveryError = this.$t('Recovery file could not be opened or saved.'); }
    },
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
      <MwListItem
        v-if="isAccountRecoverySupported || isImportSupported"
        :disabled="$walletState === $STATE_LOADING || !isImportSupported"
        :aria-description="!isImportSupported ? $t('Private-key transfers are not available yet.') : undefined"
        :title="$t('Transfer private key')"
        @click="$router.push({ name: 'crypto.import', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #before>
          <ImportIcon />
        </template>
      </MwListItem>
      <MwListItem
        v-if="isExportSupported"
        :disabled="$walletState === $STATE_LOADING"
        :title="$t('Export private keys')"
        @click="$router.push({ name: 'crypto.export', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #before>
          <ExportIcon />
        </template>
      </MwListItem>
      <MwListItem
        v-if="isAccountRecoverySupported"
        :title="$t('Save recovery file')"
        :description="recoveryError"
        @click="saveRecovery"
      >
        <template #before><ExportIcon /></template>
      </MwListItem>
      <MwListItem
        v-if="isSettingsSupported"
        :disabled="$walletState === $STATE_LOADING"
        :title="$t('Derivation path')"
        @click="$router.push({ name: 'crypto.derivation', params: { cryptoId: $wallet.crypto._id }})"
      >
        <template #before>
          <DerivationIcon />
        </template>
      </MwListItem>
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
