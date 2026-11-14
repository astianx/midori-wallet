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
    this.active = true;
    const incomingUri = typeof this.storage.uri === 'string' ? this.storage.uri.trim() : '';
    if (incomingUri) {
      this.error = undefined;
      this.uri = incomingUri;
      this.updateStorage({ uri: undefined });
    }
    this.isQrScanAvailable = await isQrScanAvailable();
    try {
      const client = await this.$account.walletConnect();
      if (!this.active) return;
      if (client.session) {
        this.updateStorage({ session: client.session });
        this.replace('main');
        return;
      }
      if (incomingUri) await this.connect();
    } catch (error) { if (this.active) this.error = this.$t(error.message); }
  },
  onHide() { this.active = false; },
  data() {
    return {
      isLoading: false,
      active: false,
      isPasteAvailable: typeof navigator.clipboard?.readText === 'function',
      isQrScanAvailable: false,
      uri: '',
      error: undefined,
    };
  },
  methods: {
    async connect() {
      if (this.isLoading || !this.active || !this.uri.trim()) return;
      this.isLoading = true;
      this.error = undefined;
      try {
        const walletConnect = await this.$account.walletConnect();
        const proposal = await walletConnect.pair(this.uri);
        if (!this.active) { await walletConnect.rejectProposal(proposal); return; }
        try { walletConnect.reviewProposal(proposal); }
        catch (error) { await walletConnect.rejectProposal(proposal); throw error; }
        this.updateStorage({ proposal });
        this.next('proposal');
      } catch (err) {
        if (!this.active) return;
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
        this.error = typeof err.code === 'number' ? this.$t(err.message) : this.$account.unknownError(err);
        console.error('Midori Wallet operation failed');
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
      <p>{{ $t('Open the app you want to connect, choose WalletConnect, then paste its connection link or scan its QR code here.') }}</p>
      <p>{{ $t('Links opened from an app go directly to permission review.') }}</p>
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
        :disabled="!uri.trim()"
        @click="connect"
      >
        {{ $t('Connect') }}
      </MwButton>
      <MwButton
        type="primary-link"
        @click="$safeOpen('https://github.com/astianx/midori-wallet/issues')"
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
