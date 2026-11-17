<script>
import MwListItem from '../../../components/MwListItem.vue';
import MwListItems from '../../../components/MwListItems.vue';
import MwStep from '../../../components/MwStep.vue';
import MwSwitch from '../../../components/MwSwitch.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { TYPES } from '../../../lib/account/Biometry.js';
import { walletSeed } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwListItem,
    MwListItems,
    MwSwitch,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isBiometryEnabled: this.$account.biometry.isEnabled,
      isHighSecurityEnabled: this.$account.settings.get('1faWallet'),
      isLoading: false, error: '',
    };
  },
  computed: {
    labels() {
      const { $t } = this;
      switch (this.$account.biometry.type) {
        case TYPES.BIOMETRICS:
          return {
            title: $t('PIN & Biometrics'),
            biometry: $t('Biometrics'),
            description: $t('Use Biometrics instead of PIN.'),
          };
        case TYPES.FINGERPRINT:
          return {
            title: $t('PIN & Fingerprint'),
            biometry: $t('Fingerprint'),
            description: $t('Use Fingerprint instead of PIN.'),
          };
        case TYPES.TOUCH_ID:
          return {
            title: $t('PIN & Touch ID'),
            biometry: $t('Touch ID'),
            description: $t('Use Touch ID instead of PIN.'),
          };
        case TYPES.FACE_ID:
          return {
            title: $t('PIN & Face ID'),
            biometry: $t('Face ID'),
            description: $t('Use Face ID instead of PIN.'),
          };
        default:
          return {
            title: $t('PIN'),
          };
      }
    },
  },
  methods: {
    async toggleBiometry() {
      this.isLoading = true;
      if (this.env.VITE_BUILD_TYPE === 'phonegap') return this.toggleBiometryPhongap();
      await this.walletSeed(async (walletSeed) => {
        if (this.isBiometryEnabled) {
          await this.$account.biometry.disable(walletSeed);
        } else {
          await this.$account.biometry.enable(undefined, walletSeed);
        }
        this.isBiometryEnabled = this.$account.biometry.isEnabled;
      }, { keepStep: true });
      this.isLoading = false;
    },
    async toggleBiometryPhongap() {
      if (this.isBiometryEnabled) {
        await this.$account.biometry.disable();
        this.isBiometryEnabled = this.$account.biometry.isEnabled;
      } else {
        this.next('pin', {
          mode: 'deviceSeed',
          layout: 'MainLayout',
          success: async (_, pin) => {
            await this.$account.biometry.enable(pin);
            this.isBiometryEnabled = this.$account.biometry.isEnabled;
            this.back();
          },
        });
      }
      this.isLoading = false;
    },
    async toggleHighSecurity() {
      this.isLoading = true;
      await this.walletSeed(async (walletSeed) => {
        try { await this.$account.settings.set('1faWallet', !this.isHighSecurityEnabled, walletSeed); this.isHighSecurityEnabled = this.$account.settings.get('1faWallet'); }
        catch { this.error = this.$t('Could not save changes. Try again.'); }
      }, { keepStep: true });
      this.isLoading = false;
    },
  },
};
</script>

<template>
  <MainLayout
    :title="labels.title"
  >
    <MwListItems>
      <MwListItem
        v-if="$account.biometry.isAvailable"
        :title="labels.biometry"
        :description="labels.description"
        :arrow="false"
      >
        <template #after>
          <MwSwitch
            :checked="isBiometryEnabled"
            :isLoading="isLoading"
            :aria-label="labels.biometry"
            @click="toggleBiometry"
          />
        </template>
      </MwListItem>
      <MwListItem
        :title="$t('High security')"
        :description="$t('Extra PIN checks protect recovery tools and security settings.')"
        :arrow="false"
      >
        <template #after>
          <MwSwitch
            :checked="isHighSecurityEnabled"
            :isLoading="isLoading"
            :aria-label="$t('High security')"
            @click="toggleHighSecurity"
          />
        </template>
      </MwListItem>
    </MwListItems>
    <p v-if="error" role="alert">{{ error }}</p>
  </MainLayout>
</template>
