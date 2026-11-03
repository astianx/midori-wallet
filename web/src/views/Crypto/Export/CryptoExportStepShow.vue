<script>
import MwButton from '../../../components/MwButton.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { saveRecoveryFile } from '../../../lib/storage/RecoveryFiles.js';
import { onShowOnHide } from '../../../lib/mixins.js';
import CopyIcon from '../../../assets/svg/copy.svg';

export default {
  components: {
    MainLayout,
    MwButton,
    CopyIcon,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  onHide() { this.storage.data?.splice(0); this.updateStorage({ data: [] }); },
  data() {
    return {
      isCopied: false,
      recoveryError: '',
    };
  },
  methods: {
    async saveRecovery() {
      try { await saveRecoveryFile(this.$account.recoveryDescriptor); this.recoveryError = ''; }
      catch { this.recoveryError = this.$t('Recovery file could not be opened or saved.'); }
    },
    copyToClipboard() {
      navigator.clipboard.writeText(JSON.stringify(this.storage.data, null, 2)).then(() => {
        this.isCopied = true;
        setTimeout(() => {
          this.isCopied = false;
        }, 1000);
      }, () => {});
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Export private keys')"
  >
    <div v-if="$wallet?.isAccountRecoverySupported" class="&__item-subtitle">
      {{ $t('This owner key controls your Safe account. Keep your 12 words safe to restore your wallet.') }}
    </div>
    <div
      v-if="storage.data.length === 0"
      class="&__empty"
    >
      {{ $t('Your wallet has no private keys with coins for export.') }}
    </div>

    <div
      v-if="storage.data.length !== 0"
      class="&__container"
    >
      <div
        class="&__keys &__group"
      >
        <div
          v-for="(item, index) in storage.data"
          :key="index"
          class="&__data"
        >
          <template
            v-for="[key, label] in [
              ['address', $wallet?.isAccountRecoverySupported ? $t('Safe account address') : $t('Public address')],
              ['ownerAddress', $t('Owner address')],
              ['derivationPath', $t('Derivation path')],
              ['privatekey', $t('Private key')],
              ['ownerPublicKey', $t('Owner public key')],
              ['ownerPrivateKey', $t('Owner private key')],
              ['activePublicKey', $t('Active public key')],
              ['activePrivateKey', $t('Active private key')],
              ['secret', $t('Secret')],
              ['publicviewkey', $t('Public view key')],
              ['privateviewkey', $t('Private view key')],
              ['publicspendkey', $t('Public spend key')],
              ['privatespendkey', $t('Private spend key')],
            ]"
            :key="key"
          >
            <div
              v-if="item[key]"
              class="&__item"
            >
              <div class="&__item-title">
                {{ label }}
              </div>
              <div
                class="&__item-subtitle"
                dir="ltr"
              >
                {{ item[key] }}
              </div>
            </div>
          </template>
        </div>
      </div>
      <MwButton v-if="$wallet?.isAccountRecoverySupported" type="primary-link" @click="saveRecovery">{{ $t('Save recovery file') }}</MwButton>
      <div v-if="recoveryError" role="alert">{{ recoveryError }}</div>
      <MwButton
        type="primary-link"
        @click="copyToClipboard"
      >
        <CopyIcon class="&__copy-icon" />
        {{ isCopied ? $t('Copied!') : $t('Copy') }}
      </MwButton>
    </div>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      gap: var(--spacing-3xl);
    }

    &__group {
      &:last-of-type {
        flex-grow: 1;
      }
    }

    &__empty {
      @include text-md;
    }

    &__keys {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-3xl);
    }

    &__data {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-lg);
    }

    &__item {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-2xs);
    }

    &__item-title {
      @include text-md;
      @include text-bold;
    }

    &__item-subtitle {
      @include text-sm;
      word-break: break-all;
    }
  }
</style>
