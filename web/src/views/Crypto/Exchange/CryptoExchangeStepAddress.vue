<script>
import BaseExchange from '../../../lib/exchanges/BaseExchange.js';
import { errors } from '../../../lib/account/domain.js';

import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwCryptoLogo from '../../../components/MwCryptoLogo.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwPoweredBy from '../../../components/MwPoweredBy.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import EditIcon from '../../../assets/svg/edit.svg';
import LocationIcon from '../../../assets/svg/location.svg';
import PasteIcon from '../../../assets/svg/paste.svg';
import QrIcon from '../../../assets/svg/qr.svg';
import WalletSmallIcon from '../../../assets/svg/walletSmall.svg';

import { onShowOnHide } from '../../../lib/mixins.js';
import {
  cryptoSubtitle,
  isQrScanAvailable,
} from '../../../lib/helpers.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwCryptoLogo,
    MwFormGroup,
    MwFormInput,
    MwFormTextareaReadonly,
    MwPoweredBy,
    EditIcon,
    LocationIcon,
    PasteIcon,
    QrIcon,
    WalletSmallIcon,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  async onShow() {
    if (this.args?.error) {
      this.error = this.$t('Invalid address');
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
      isUnaliasSupported: this.$account.wallet(this.storage.to.crypto._id)?.isUnaliasSupported,
      address: this.storage.address,
      isQrScanAvailable: false,
      isPasteAvailable: typeof navigator.clipboard?.readText === 'function',
      ownWallet: this.storage.address === 'your wallet',
      subtitle: cryptoSubtitle(this.$wallet),
      error: undefined,
    };
  },
  methods: {
    switchWallet() {
      if (this.ownWallet) {
        this.ownWallet = false;
        this.address = undefined;
        this.error = undefined;
      } else {
        this.ownWallet = true;
        this.address = 'your wallet';
      }
    },
    async confirm() {
      if (this.ownWallet) {
        this.next('confirm');
      } else {
        this.isLoading = true;
        this.error = undefined;
        let { address } = this;
        let alias;
        let extraId;
        try {
          if (this.isUnaliasSupported) {
            const data = await this.$account.wallet(this.storage.to.crypto._id).unalias(address);
            if (data) {
              ({ address } = data);
              ({ alias } = data);
              if (this.storage.to.crypto.platform === 'ripple') {
                extraId = data.destinationTag;
              }
            }
          }

          await this.$account.exchanges.validateAddress({
            to: this.storage.to.crypto._id,
            address,
            extraId,
            provider: this.storage.provider,
          });
          this.updateStorage({
            address,
            alias,
            extraId,
          });
          if (BaseExchange.EXTRA_ID.includes(this.storage.to.crypto._id) && extraId === undefined) {
            this.next('meta');
          } else {
            this.next('confirm');
          }
        } catch (err) {
          if (err instanceof errors.EmptyAddressError) {
            this.error = this.$t('Address should not be empty');
            return;
          }
          if (err instanceof errors.InvalidAddressError) {
            this.error = this.$t('Invalid address');
            return;
          }
          console.error(err);
        } finally {
          this.isLoading = false;
        }
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
  <MainLayout
    :title="$t('Swap {symbol}', { symbol: $wallet.crypto.symbol })"
    :description="subtitle"
  >
    <MwFormGroup class="&__container">
      <MwFormTextareaReadonly
        :label="$t('Destination blockchain')"
        :value="storage.to.platform.name"
        inheritDir
      >
        <template #before>
          <MwCryptoLogo
            :crypto="storage.to.platform"
          />
        </template>
      </MwFormTextareaReadonly>
      <MwFormTextareaReadonly
        v-if="address === 'your wallet'"
        :label="$t('Wallet address')"
        :value="$t('Your wallet')"
        inheritDir
      >
        <template #before>
          <WalletSmallIcon />
        </template>
        <template #after>
          <EditIcon
            class="&__switch"
            @click="switchWallet"
          />
        </template>
      </MwFormTextareaReadonly>
      <MwFormInput
        v-if="!ownWallet"
        v-model="address"
        :label="$t('Wallet address')"
        :error="error"
        :clear="true"
        @update:modelValue="error = undefined"
      />

      <MwButtonGroup
        v-if="!ownWallet"
        class="&__actions"
        type="circle"
      >
        <MwButton
          v-if="$account.wallet(storage.to.crypto._id)"
          type="circle"
          @click="switchWallet"
        >
          <template #circle>
            <WalletSmallIcon />
          </template>
          {{ $t('Your wallet') }}
        </MwButton>
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
          v-if="storage.to.crypto.supported"
          type="circle"
          @click="next('mecto')"
        >
          <template #circle>
            <LocationIcon />
          </template>
          {{ $t('Mecto') }}
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
      <MwPoweredBy :powered="$account.exchanges.getProviderInfo(storage.provider)" />
    </MwButtonGroup>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      flex-grow: 1;
    }

    &__switch {
      cursor: pointer;
    }

    &__actions {
      width: 100%;
      max-width: 25rem;
      align-self: center;
    }
  }
</style>
