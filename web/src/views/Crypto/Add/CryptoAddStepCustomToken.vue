<script>
import { isQrScanAvailable } from '../../../lib/helpers.js';
import { onShowOnHide, walletSeed } from '../../../lib/mixins.js';

import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwCryptoLogo from '../../../components/MwCryptoLogo.vue';
import MwFormDropdown from '../../../components/MwForm/MwFormDropdown.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { AddressError, RequestError } from '../../../lib/ui-runtime/vendor/errors.js';

import PasteIcon from '../../../assets/svg/paste.svg';
import QrIcon from '../../../assets/svg/qr.svg';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwCryptoLogo,
    MwFormDropdown,
    MwFormGroup,
    MwFormInput,
    PasteIcon,
    QrIcon,
  },
  extends: MwStep,
  mixins: [walletSeed, onShowOnHide],
  async onShow() {
    if (this.args?.error) {
      this.error = this.$t('Invalid contract address');
    }
    if (this.storage.temp?.address) {
      this.error = undefined;
      this.address = this.storage.temp.address;
      this.storage.temp.address = undefined;
    }
    this.isQrScanAvailable = await isQrScanAvailable();
  },
  data() {
    return {
      isLoading: false,
      showModal: false,
      address: '',
      error: undefined,
      isPasteAvailable: typeof navigator.clipboard?.readText === 'function',
      isQrScanAvailable: false,
    };
  },
  computed: {
    platform() {
      return this.$account.cryptoDB.get(this.storage.platform);
    },
  },
  watch: {
    'storage.platform'(newPlatform, oldPlatform) {
      if (newPlatform !== oldPlatform) {
        this.error = undefined;
        this.address = '';
        this.updateStorage({ token: undefined });
      }
    },
  },
  methods: {
    async confirm() {
      if (this.isLoading) return;
      if (!this.address) {
        this.error = this.$t('Invalid contract address');
        return;
      }
      this.isLoading = true;
      this.error = undefined;
      this.updateStorage({ token: undefined });

      try {
        const token = await this.$account.getCustomTokenInfo(this.platform.platform, this.address);
        if (this.$account.hasWallet(token._id)) {
          this.error = this.$t('{token} already added.', {
            token: token.name,
          });
          return;
        }
        const url = await this.$account.getTokenUrl(this.platform.platform, this.address);
        this.updateStorage({ token, url });
        this.next('tokenInfo');
      } catch (err) {
        if (err instanceof AddressError || err instanceof RequestError) {
          this.error = this.$t('Invalid contract address');
          return;
        }
        console.error(err);
      } finally {
        this.isLoading = false;
      }
    },
    paste() {
      navigator.clipboard.readText()
        .then((text) => {
          this.error = undefined;
          this.address = text;
        }, () => {});
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Add custom token')">
    <MwFormGroup class="&__container">
      <MwFormDropdown
        :value="platform.name"
        :label="$t('Blockchain')"
        @click="next('selectBlockchain')"
      >
        <template #before>
          <MwCryptoLogo :crypto="platform" />
        </template>
      </MwFormDropdown>
      <MwFormInput
        v-model="address"
        :label="$t('Contract address')"
        clear
        :error="error"
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
    <MwButtonGroup>
      <MwButton
        type="primary"
        :isLoading="isLoading"
        @click="confirm"
      >
        {{ $t('Continue') }}
      </MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__container {
      flex-grow: 1;
    }
  }
</style>
