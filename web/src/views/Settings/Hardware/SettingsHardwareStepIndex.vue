<script>
import MwButton from '../../../components/MwButton.vue';
import MwLoader from '../../../components/MwLoader.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import { walletSeed } from '../../../lib/mixins.js';

import DeleteIcon from '../../../assets/svg/delete.svg';

export default {
  components: {
    MainLayout,
    MwButton,
    MwLoader,
    DeleteIcon,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      MAX_AUTHENTICATORS: 10,
      isLoading: true,
      isAdding: false,
      keys: [],
    };
  },
  async mounted() {
    await this.loadKeys();
  },
  methods: {
    async add() {
      this.isAdding = true;
      await this.walletSeed(async (walletSeed) => {
        const result = await this.$account.hardware.add(walletSeed);
        if (result) await this.loadKeys();
      }, { keepStep: true });
      this.isAdding = false;
    },
    remove(credentialID) {
      this.next('passphrase', { credentialID, loadKeys: this.loadKeys });
    },
    async loadKeys() {
      this.isLoading = true;
      this.keys = (await this.$account.hardware.list()).map((item) => {
        return {
          ...item,
          name: this.$d(new Date(item.date), 'hardware').replace(',', ''),
        };
      });
      this.$account.settings.clientSet('hasAuthenticators', this.keys.length !== 0);
      this.isLoading = false;
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Hardware security')">
    <MwLoader v-if="isLoading" />
    <template v-else>
      <div class="&__hardware-keys">
        <div v-if="keys.length">
          <div class="&__header">
            {{ $t('Hardware keys') }}
          </div>
          <div class="&__list">
            <div
              v-for="item in keys"
              :key="item.credentialID"
              :title="item.name"
              class="&__row"
            >
              {{ item.name }}
              <MwButton
                class="&__delete"
                @click="remove(item.credentialID)"
              >
                <DeleteIcon />
              </MwButton>
            </div>
          </div>
        </div>
        <div v-else>
          {{ $t('You do not have any hardware keys yet.') }}
        </div>
      </div>
      <MwButton
        v-if="keys.length < MAX_AUTHENTICATORS"
        type="primary"
        :isLoading="isAdding"
        @click="add"
      >
        {{ $t('Add hardware key') }}
      </MwButton>
    </template>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__hardware-keys {
      @include text-md;
      flex-grow: 1;
    }

    &__header {
      @include text-sm;
      margin-bottom: var(--spacing-2xs);
      color: var(--color-secondary);
    }

    &__list {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-2xs);
    }

    &__row {
      display: flex;
      height: 3.5rem;
      align-items: center;
      justify-content: space-between;
    }

    &__delete {
      width: var(--spacing-xl);
      height: var(--spacing-xl);
    }
  }
</style>
