<script>
import MwStep from '../../../components/MwStep.vue';

import MwButton from '../../../components/MwButton.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import { objectsIsEqual } from '../../../lib/helpers.js';
import { walletSeed } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormGroup,
    MwFormInput,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
      isAddressTypesSupported: this.$wallet.isAddressTypesSupported,
      settings: this.$wallet.isAddressTypesSupported ? {
        bip84: this.$wallet.settings.bip84,
        bip49: this.$wallet.settings.bip49,
        bip44: this.$wallet.settings.bip44,
      } : {
        bip44: this.$wallet.settings.bip44,
      },
      errors: {},
    };
  },
  watch: {
    settings: {
      handler(value) {
        if (this.$wallet.isDerivationPathFixed) return;
        for (const key in value) {
          this.errors[key] = this.$wallet.validateDerivationPath(value[key]) ? false : this.$t('Invalid path');
        }
      },
      deep: true,
    },
  },
  methods: {
    async save() {
      if (this.$wallet.isDerivationPathFixed) return this.$router.up();
      this.isLoading = true;
      for (const key in this.settings) {
        if (!this.$wallet.validateDerivationPath(this.settings[key])) {
          this.errors[key] = this.$t('Invalid path');
          return this.isLoading = false;
        }
      }
      if (objectsIsEqual(this.settings, this.$wallet.settings)) return this.$router.up();

      await this.walletSeed(async (walletSeed) => {
        try {
          await this.$account.updatePlatformSettings(this.$wallet, this.settings, walletSeed);
          this.$router.up();
        } catch (err) {
          console.error(err);
        }
      }, { keepStep: true });
      this.isLoading = false;
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Derivation path')">
    <div class="&__note">
      {{ $t('Derivation path defines how to derive private keys from passphrase.') }}
      <template v-if="$wallet.isDerivationPathFixed"> {{ $t('This path is fixed for your account. Changing it would create a different address.') }}</template>
    </div>
    <MwFormGroup class="&__form">
      <template v-if="isAddressTypesSupported">
        <MwFormInput
          v-model="settings.bip84"
          :label="$t('Bech32 - SegWit native')"
          :error="errors.bip84"
          clear
        />
        <MwFormInput
          v-model="settings.bip49"
          :label="$t('P2SH - SegWit compatible')"
          :error="errors.bip49"
          clear
        />
        <MwFormInput
          v-model="settings.bip44"
          :label="$t('P2PKH - Legacy')"
          :error="errors.bip44"
          clear
        />
      </template>
      <MwFormInput
        v-else
        v-model="settings.bip44"
        :readonly="$wallet.isDerivationPathFixed"
        :label="$t('Derivation path')"
        :error="errors.bip44"
        clear
      />
    </MwFormGroup>
    <MwButton
      type="primary"
      :isLoading="isLoading"
      @click="save"
    >
      {{ $wallet.isDerivationPathFixed ? $t('Done') : $t('Save') }}
    </MwButton>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__note {
      @include text-md;
    }

    &__form {
      flex-grow: 1;
    }
  }
</style>
