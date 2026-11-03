<script>
import MwAvatar from '../../components/MwAvatar.vue';
import MwButton from '../../components/MwButton.vue';
import MwListItem from '../../components/MwListItem.vue';
import MwListItemDropdown from '../../components/MwListItemDropdown.vue';
import MwListItems from '../../components/MwListItems.vue';
import MainLayout from '../../layouts/MainLayout.vue';
import { currencies } from '../../lib/account/Market.js';
import { prettyVersion } from '../../lib/version.js';

import { TYPES } from '../../lib/account/Biometry.js';
import { languages } from '../../lib/i18n/i18n.js';

export default {
  components: {
    MainLayout,
    MwAvatar,
    MwButton,
    MwListItem,
    MwListItemDropdown,
    MwListItems,
  },
  data() {
    return {
      version: prettyVersion,
      currency: this.$currency,
      currencies: currencies.map((currency) => ({ value: currency, name: currency })),
      language: this.$i18n.locale,
      languages,
      theme: this.$account.theme,
    };
  },
  computed: {
    themes() {
      return [
        { name: this.$t('System'), value: 'system' },
        { name: this.$t('Light'), value: 'light' },
        { name: this.$t('Dark'), value: 'dark' },
      ];
    },
    securityPinTitle() {
      const { $t } = this;
      switch (this.$account.biometry.type) {
        case TYPES.BIOMETRICS:
          return $t('PIN & Biometrics');
        case TYPES.FINGERPRINT:
          return $t('PIN & Fingerprint');
        case TYPES.TOUCH_ID:
          return $t('PIN & Touch ID');
        case TYPES.FACE_ID:
          return $t('PIN & Face ID');
        default:
          return $t('PIN');
      }
    },
  },
  watch: {
    async currency(value, oldValue) {
      if (value === oldValue) {
        return;
      }
      this.$account.details.set('systemInfo', {
        ...this.$account.details.get('systemInfo'),
        preferredCurrency: value,
      });
      await this.$account.details.save();
      this.$account.emit('update', 'currency');
      this.$account.emit('update');
    },
    async language(value, oldValue) {
      if (value === oldValue) {
        return;
      }
      this.$account.details.set('systemInfo', {
        ...this.$account.details.get('systemInfo'),
        language: value,
      });
      await this.$account.details.save();
      this.$account.emit('update', 'language');
    },
    async theme(value, oldValue) {
      if (value === oldValue) {
        return;
      }
      this.$account.details.set('systemInfo', {
        ...this.$account.details.get('systemInfo'),
        theme: value,
      });
      await this.$account.details.save();
      this.$account.emit('update', 'theme');
    },
  },
  methods: {
    support() {
      this.$safeOpen('https://github.com/astianx/midori-wallet/issues');
    },
    logout() {
      this.$account.logout();
      this.$router.replace({ name: 'auth' });
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Settings')">
    <div class="&__header">
      <MwAvatar
        class="&__header-avatar"
        :avatar="$user.avatar"
        own
        :class="{ '&__header-avatar--tor': $isOnion }"
        :size="80"
        :alt="$t('Account')"
      />
      <div
        v-if="$user.username"
        class="&__header-username"
      >
        {{ $user.username }}
      </div>
      <MwButton
        class="&__header-button"
        type="primary-light"
        small
        @click="$router.push({ name: 'settings.account' })"
      >
        {{ $t('Edit') }}
      </MwButton>
    </div>
    <MwListItems :title="$t('General')">
      <MwListItem
        :title="$t('Local currency')"
      >
        <template #after>
          <MwListItemDropdown
            v-model="currency"
            :options="currencies"
            :aria-label="$t('Local currency')"
          />
        </template>
      </MwListItem>
      <MwListItem
        :title="$t('Language')"
      >
        <template #after>
          <MwListItemDropdown
            v-model="language"
            :options="languages"
            :aria-label="$t('Language')"
          />
        </template>
      </MwListItem>
      <MwListItem
        :title="$t('Theme')"
      >
        <template #after>
          <MwListItemDropdown
            v-model="theme"
            :options="themes"
            :aria-label="$t('Theme')"
          />
        </template>
      </MwListItem>
    </MwListItems>

    <MwListItems :title="$t('Connections')">
      <MwListItem
        :title="$t('WalletConnect')"
        @click="$router.push({ name: 'settings.walletconnect' })"
      />
    </MwListItems>

    <MwListItems :title="$t('Security')">
      <MwListItem
        :title="securityPinTitle"
        @click="$router.push({ name: 'settings.pin' })"
      />
      <MwListItem
        :title="$t('Recovery file')"
        @click="$router.push({ name: 'settings.recovery' })"
      />
      <MwListItem
        :title="$t('Hardware security')"
        @click="$router.push({ name: 'settings.hardware' })"
      />
    </MwListItems>

    <MwListItems
      v-if="['phonegap', 'electron'].includes(env.VITE_BUILD_TYPE)"
      :title="$t('Privacy')"
    >
      <MwListItem
        :title="$t('Tor')"
        @click="$router.push({ name: 'settings.tor' })"
      />
    </MwListItems>

    <MwListItems :title="$t('Support')">
      <MwListItem
        :title="$t('Support (English)')"
        @click="support"
      />
    </MwListItems>

    <MwListItems :title="$t('About')">
      <MwListItem
        :title="$t('Terms of Service')"
        @click="$safeOpen(`${$account.siteUrl}terms-of-service/`)"
      />
      <MwListItem
        :title="$t('Privacy Policy')"
        @click="$safeOpen(`${$account.siteUrl}privacy-policy/`)"
      />
    </MwListItems>

    <MwListItems>
      <MwListItem
        :arrow="false"
        type="danger"
        :title="$t('Log out')"
        class="&__logout"
        @click="logout"
      />
    </MwListItems>

    <div class="&__version">
      {{ version }}
    </div>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__header {
      z-index: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--spacing-lg);
    }

    &__header-avatar {
      z-index: -1;
      width: var(--spacing-6xl);
      height: var(--spacing-6xl);
    }

    &__header-username {
      @include text-md;
      @include ellipsis;
      max-width: 100%;
    }

    &__version {
      @include text-sm;
      color: var(--color-secondary);
      text-align: center;
    }
  }
</style>
