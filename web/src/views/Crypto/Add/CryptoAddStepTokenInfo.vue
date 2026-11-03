<script>
import { cryptoSubtitleWithSymbol } from '../../../lib/helpers.js';
import { walletSeed } from '../../../lib/mixins.js';

import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
import MwModal from '../../../components/MwModal.vue';
import MwStep from '../../../components/MwStep.vue';
import MwTokenInfo from '../../../components/MwTokenInfo.vue';
import MwWarning from '../../../components/MwWarning.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { SeedRequiredError } from '../../../lib/account/Account.js';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    MwFormGroup,
    MwFormTextareaReadonly,
    MwModal,
    MwTokenInfo,
    MwWarning,
    MainLayout,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
      showModal: false,
    };
  },
  computed: {
    platform() {
      return this.$account.cryptoDB.get(this.storage.platform);
    },
    token() {
      return this.storage.token;
    },
    url() {
      return this.storage.url;
    },
    description() {
      return [
        cryptoSubtitleWithSymbol({ crypto: this.token, platform: this.platform }),
        this.$t('{decimals} decimals', { decimals: this.token.decimals }),
      ];
    },
  },
  methods: {
    async add(force) {
      if (!force && !this.$account.hasWallet(this.platform._id)) {
        this.showModal = true;
        return;
      }
      this.showModal = false;
      this.isLoading = true;
      this.error = undefined;
      try {
        await this.$account.addWallet(this.token);
        this.$router.replace({ name: 'crypto', params: { cryptoId: this.token._id } });
      } catch (err) {
        if (err instanceof SeedRequiredError) {
          await this.walletSeed(async (walletSeed) => {
            await this.$account.addWallet(this.token, walletSeed);
            this.$router.replace({ name: 'crypto', params: { cryptoId: this.token._id } });
          });
        } else {
          console.error('Midori Wallet operation failed');
        }
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Add custom token')">
    <MwTokenInfo
      class="&__container"
      :crypto="token"
      :platform="platform"
      :title="token.name"
      :subtitles="description"
    />
    <MwFormGroup>
      <MwFormTextareaReadonly
        :label="$t('Contract address')"
        :value="token.address"
      />
      <MwWarning
        v-if="token.custom === true"
        class="&__warning"
        @click="$safeOpen('https://github.com/astianx/midori-wallet/issues')"
      >
        {{ $t('Adding unknown tokens can be risky, so only add tokens you trust. Learn more.') }}
      </MwWarning>
      <MwButton
        v-if="url"
        type="primary-link"
        @click="$safeOpen(url)"
      >
        {{ $t('View in Block Explorer') }}
      </MwButton>
    </MwFormGroup>
    <MwButtonGroup>
      <MwButton
        type="primary"
        :isLoading="isLoading"
        @click="add"
      >
        {{ $t('Add') }}
      </MwButton>
    </MwButtonGroup>

    <MwModal
      :show="showModal"
      :title="$t('Add token')"
      @close="showModal = false"
    >
      {{ $t("{crypto} is based on {platform}, which isn't in your wallet yet, so it will also be added.", {
        platform: platform.name,
        crypto: $t('Custom token')
      }) }}
      <template #footer>
        <MwButtonGroup>
          <MwButton
            type="primary"
            @click="add(true)"
          >
            {{ $t('Add') }}
          </MwButton>
          <MwButton
            type="primary-link"
            @click="showModal = false"
          >
            {{ $t('Cancel') }}
          </MwButton>
        </MwButtonGroup>
      </template>
    </MwModal>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__container {
      flex-grow: 1;
    }

    &__warning {
      cursor: pointer;
    }
  }
</style>
