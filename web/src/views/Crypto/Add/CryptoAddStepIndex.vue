<script>
import Fuse from 'fuse.js/dist/fuse.basic.esm.js';
import { SeedRequiredError } from '../../../lib/account/Account.js';
import { cryptoSubtitleWithSymbol } from '../../../lib/helpers.js';
import { walletSeed } from '../../../lib/mixins.js';

import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwCryptoList from '../../../components/MwCryptoList.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwModal from '../../../components/MwModal.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import FilterIcon from '../../../assets/svg/filter.svg';
import PlusIcon from '../../../assets/svg/plus.svg';
import SearchIcon from '../../../assets/svg/search.svg';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwCryptoList,
    MwFormInput,
    MwModal,
    FilterIcon,
    PlusIcon,
    SearchIcon,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    let showModal = false;
    let selected = undefined;
    const alreadyAdded = this.$account.wallets().map((item) => item.crypto._id);
    const cryptos = this.$account.cryptoDB.all
      .filter((item) => item.supported && !item.deprecated)
      .filter((item) => !alreadyAdded.includes(item._id))
      .map((crypto) => {
        const platform = this.$account.cryptoDB.platform(crypto.platform);
        return {
          title: crypto.name,
          subtitle: cryptoSubtitleWithSymbol({ crypto, platform }),
          crypto,
          platform,
        };
      });

    const coinsList = cryptos.filter((item) => item.crypto.type === 'coin');
    const tokensList = cryptos.filter((item) => item.crypto.type === 'token');

    const coinsIndex = new Fuse(coinsList, {
      keys: ['crypto.name', 'crypto.symbol', 'crypto.address', 'crypto._id'],
      threshold: 0.4,
    });
    const tokensIndex = new Fuse(tokensList, {
      keys: ['crypto.name', 'crypto.symbol', 'crypto._id'],
      threshold: 0.5,
    });
    if (this.$route.query.cryptoId && !alreadyAdded.includes(this.$route.query.cryptoId)) {
      const crypto = this.$account.cryptoDB.get(this.$route.query.cryptoId);
      if (crypto) {
        const platform = this.$account.cryptoDB.platform(crypto.platform);
        showModal = true;
        selected = {
          crypto,
          platform,
        };
      }
    }
    return {
      query: '',
      showModal,
      selected,
      isLoading: false,
      alreadyAdded,
      coinsList,
      tokensList,
      coinsIndex,
      tokensIndex,
    };
  },
  computed: {
    coins() {
      const coins = this.query ? this.coinsIndex.search(this.query).map(item => item.item) : this.coinsList;
      if (this.storage.filterPlatform) {
        return coins.filter((item) => item.platform._id === this.storage.filterPlatform);
      }
      return coins;
    },
    tokens() {
      let tokens = this.tokensList;
      if (this.query) {
        const exactByAddress = this.tokensList.filter(
          token => token.crypto.address?.toLowerCase() === this.query.toLowerCase()
        );
        const exactIds = new Set(exactByAddress.map(t => t.crypto._id));
        const fuzzy = this.tokensIndex.search(this.query)
          .map(item => item.item)
          .filter(item => !exactIds.has(item.crypto._id));
        tokens = [...exactByAddress, ...fuzzy];
      }
      if (this.storage.filterPlatform) {
        return tokens.filter((item) => item.platform._id === this.storage.filterPlatform);
      }
      return tokens;
    },
  },
  methods: {
    async select(id) {
      const crypto = this.$account.cryptoDB.get(id);
      if (crypto.type === 'coin') {
        await this.add(crypto);
      }
      if (crypto.type === 'token') {
        const platform = this.$account.cryptoDB.platform(crypto.platform);
        if (this.alreadyAdded.includes(platform._id)) {
          await this.add(crypto);
        } else {
          this.selected = {
            crypto,
            platform,
          };
          this.showModal = true;
        }
      }
    },
    async add(crypto) {
      this.showModal = false;
      this.isLoading = true;
      try {
        await this.$account.addWallet(crypto);
        this.$router.replace({ name: 'crypto', params: { cryptoId: crypto._id } });
      } catch (err) {
        if (err instanceof SeedRequiredError) {
          await this.walletSeed(async (walletSeed) => {
            await this.$account.addWallet(crypto, walletSeed);
            this.$router.replace({ name: 'crypto', params: { cryptoId: crypto._id } });
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
  <MainLayout
    :title="$t('Add crypto')"
    wide
  >
    <div class="&__actions">
      <div class="&__action-search">
        <MwFormInput
          v-model="query"
          class="&__action-input"
          :placeholder="$t('Search')"
          :ariaLabel="$t('Search')"
          small
          clear
        >
          <template #before>
            <SearchIcon />
          </template>
          <template #button>
            <MwButton
              small
              :type="storage.filterPlatform ? 'primary-light' : 'secondary'"
              @click="next('filterBlockchain')"
            >
              <FilterIcon />
            </MwButton>
          </template>
        </MwFormInput>
      </div>
      <div class="&__action-add-custom">
        <MwButton
          small
          type="primary-link"
          @click="next('customToken')"
        >
          <PlusIcon />
          {{ $t('Add custom token') }}
        </MwButton>
      </div>
    </div>
    <div
      v-if="coins.length === 0 && tokens.length === 0"
      class="&__message"
    >
      {{ $t('No coins or tokens found.') }}
    </div>
    <MwCryptoList
      :header="$t('Coins')"
      class="&__list"
      :items="coins"
      :isLoading="isLoading"
      columns
      @select="select"
    />
    <MwCryptoList
      :header="$t('Tokens')"
      class="&__list &__list--last"
      :items="tokens"
      :isLoading="isLoading"
      columns
      @select="select"
    />
    <MwModal
      :show="showModal"
      :title="selected?.crypto?.type === 'coin' ? $t('Add coin') : $t('Add token')"
      @close="showModal = false"
    >
      <div v-if="$route.query.cryptoId">
        {{ $t('Do you want to add {crypto} to your wallet?', {
          crypto: selected.crypto.name,
        }) }}
      </div>
      <div v-if="selected.crypto.type === 'token' && !alreadyAdded.includes(selected.platform._id)">
        {{ $t("{crypto} is based on {platform}, which isn't in your wallet yet, so it will also be added.", {
          platform: selected.platform.name,
          crypto: selected.crypto.name,
        }) }}
      </div>
      <template #footer>
        <MwButtonGroup>
          <MwButton
            type="primary"
            @click="add(selected.crypto)"
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
    &__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      @include breakpoint(lg) {
        flex-wrap: nowrap;
      }
    }

    &__action-search {
      flex-basis: 100%;
      margin-bottom: var(--spacing-3xl);
      @include breakpoint(lg) {
        flex-basis: 50%;
        margin-bottom: 0;
      }
    }

    &__message {
      @include text-md;
    }

    &__action-add-custom {
      display: flex;
      flex-basis: 100%;
      justify-content: center;
      @include breakpoint(lg) {
        flex-basis: 50%;
      }
    }
  }
</style>
