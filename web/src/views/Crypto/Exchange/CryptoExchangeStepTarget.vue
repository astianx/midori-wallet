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
    SearchIcon,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      query: '',
      showModal: false,
      selected: undefined,
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
  beforeCreate() {
    const cryptos = this.$account.cryptoDB.all
      .filter((item) => {
        return !item.deprecated
          && this.$account.exchanges.isSupported(this.$wallet.crypto, item)
          && item._id !== this.$wallet.crypto._id;
      })
      .map((crypto) => {
        const platform = this.$account.cryptoDB.platform(crypto.platform);
        return {
          title: crypto.name,
          subtitle: cryptoSubtitleWithSymbol({ crypto, platform }),
          crypto,
          platform,
        };
      });

    this.coinsList = cryptos.filter((item) => item.crypto.type === 'coin');
    this.tokensList = cryptos.filter((item) => item.crypto.type === 'token');

    this.coinsIndex = new Fuse(this.coinsList, {
      keys: ['crypto.name', 'crypto.symbol', 'crypto._id'],
      threshold: 0.4,
    });
    this.tokensIndex = new Fuse(this.tokensList, {
      keys: ['crypto.name', 'crypto.symbol', 'crypto._id'],
      threshold: 0.5,
    });
  },
  beforeUnmount() {
    this.updateStorage({ filterPlatform: undefined });
  },
  methods: {
    select(id) {
      const crypto = this.$account.cryptoDB.get(id);
      const platform = this.$account.cryptoDB.platform(crypto.platform);
      this.updateStorage({
        to: { crypto, platform },
      });
      if (!crypto.supported || this.$account.wallet(crypto._id)) {
        this.back();
      } else {
        this.showModal = true;
      }
    },
    async add(crypto) {
      this.showModal = false;
      try {
        await this.$account.addWallet(crypto);
        this.back();
      } catch (err) {
        if (err instanceof SeedRequiredError) {
          await this.walletSeed(async (walletSeed) => {
            await this.$account.addWallet(crypto, walletSeed);
            this.back();
          }, { keepStep: true });
        } else {
          console.error('Midori Wallet operation failed');
        }
      }
    },
    skip() {
      this.showModal = false;
      this.back();
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Select target crypto')"
    wide
  >
    <div class="&__actions">
      <div class="&__action-search">
        <MwFormInput
          v-model="query"
          class="&__action-input"
          :placeholder="$t('Search')"
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
      :selected="storage.toId"
      columns
      @select="select"
    />
    <MwCryptoList
      :header="$t('Tokens')"
      class="&__list &__list--last"
      :items="tokens"
      :selected="storage.toId"
      columns
      @select="select"
    />
    <MwModal
      :show="showModal"
      :title="storage?.to?.crypto?.type === 'coin' ? $t('Add coin') : $t('Add token')"
      @close="skip"
    >
      <div>
        {{ $t('Do you want to add {crypto} to your wallet?', {
          crypto: storage.to.crypto.name,
        }) }}
      </div>
      <div v-if="storage.to.crypto.type === 'token' && !$account.wallet(storage.to.platform._id)">
        {{ $t("{crypto} is based on {platform}, which isn't in your wallet yet, so it will also be added.", {
          platform: storage.to.platform.name,
          crypto: storage.to.crypto.name,
        }) }}
      </div>
      <template #footer>
        <MwButtonGroup>
          <MwButton
            type="primary"
            @click="add(storage.to.crypto)"
          >
            {{ $t('Add') }}
          </MwButton>
          <MwButton
            type="primary-link"
            @click="skip"
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
      @include breakpoint(lg) {
        flex-basis: 50%;
        margin-bottom: 0;
      }
    }

    &__message {
      @include text-md;
    }
  }
</style>
