<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import PasteIcon from '../../../assets/svg/paste.svg';
import QrIcon from '../../../assets/svg/qr.svg';

import { isQrScanAvailable } from '../../../lib/helpers.js';
import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwFormGroup,
    MwFormInput,
    PasteIcon,
    QrIcon,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  async onShow() {
    if (this.storage.uri) {
      this.error = undefined;
      this.uri = this.storage.uri;
      this.storage.uri = undefined;
    }
    this.isQrScanAvailable = await isQrScanAvailable();
  },
  data() {
    return {
      isLoading: false,
      isPasteAvailable: typeof navigator.clipboard?.readText === 'function',
      isQrScanAvailable: false,
      uri: '',
      error: undefined,
    };
  },
  methods: {
    async connect() {
      this.isLoading = true;
      this.error = undefined;
      try {
        const walletConnect = await this.$account.walletConnect();
        const proposal = await walletConnect.pair(this.uri);
        const session = await walletConnect.approveSession(proposal);
        this.updateStorage({ session });
        this.next('main');
      } catch (err) {
        if (err.message?.startsWith?.('Missing or invalid. pair() uri')) {
          this.error = this.$t('Invalid URI');
          return;
        }
        if (err.message?.includes?.('Please try again with a new connection URI')) {
          this.error = this.$t('Please try again with a new connection URI');
          return;
        }
        if (err.message?.includes?.('Non conforming namespaces. approve() namespaces chains')) {
          this.error = this.$t('Please add missing coins to your wallet and try again');
          return;
        }
        if (err.message?.includes?.('Non conforming namespaces')) {
          this.error = this.$t('Not supported');
          return;
        }
        if (err.message?.includes?.('Not supported')) {
          this.error = this.$t('Not supported');
          return;
        }
        if (err.message?.includes?.('No internet connection detected')) {
          this.error = this.$t('Error! Please check your internet connection and try again.');
          return;
        }
        this.error = this.$account.unknownError(err);
        console.error(err);
      } finally {
        this.isLoading = false;
      }
    },
    paste() {
      navigator.clipboard.readText()
        .then((text) => {
          this.error = undefined;
          this.uri = text;
        }, () => {});
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('WalletConnect')"
  >
    <MwFormGroup class="&__container">
      <MwFormInput
        v-model="uri"
        :label="$t('WalletConnect URI')"
        :error="error"
        :clear="true"
        @update:modelValue="error = undefined"
      />

      <MwButtonGroup
        class="&__actions"
        type="circle"
      >
        <MwButton
          v-if="isPasteAvailable"
          type="circle"
          @click="paste"
        >
          <template #circle>
            <PasteIcon />
          </template>
          {{ $t('Paste') }}
        </MwButton>
        <MwButton
          v-if="isQrScanAvailable"
          type="circle"
          @click="next('qr')"
        >
          <template #circle>
            <QrIcon />
          </template>
          {{ $t('Scan QR') }}
        </MwButton>
      </MwButtonGroup>
    </MwFormGroup>
    <MwButtonGroup class="&__buttons">
      <MwButton
        type="primary"
        :isLoading="isLoading"
        @click="connect"
      >
        {{ $t('Connect') }}
      </MwButton>
      <MwButton
        type="primary-link"
        @click="$safeOpen('https://astian.org/midori-wallet/')"
      >
        {{ $t('What is WalletConnect?') }}
      </MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      flex-grow: 1;
    }

    &__actions {
      width: 100%;
      max-width: 25rem;
      align-self: center;
    }

    &__buttons {
      flex-shrink: 0;
    }
  }
</style>
