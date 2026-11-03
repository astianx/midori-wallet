<script>
import MwLoader from '../../../components/MwLoader.vue';
import MwNavbar from '../../../components/MwNavbar.vue';
import MwNavbarButton from '../../../components/MwNavbarButton.vue';
import MwRefreshButton from '../../../components/MwRefreshButton.vue';
import MwStep from '../../../components/MwStep.vue';

import CryptoHistoryRow from './CryptoHistoryRow.vue';
import SmartLayout from '../../../layouts/SmartLayout.vue';

import { onShowOnHide } from '../../../lib/mixins.js';
import ArrowLeftIcon from '../../../assets/svg/arrowLeft.svg';

export default {
  components: {
    SmartLayout,
    MwLoader,
    MwNavbar,
    MwNavbarButton,
    MwRefreshButton,
    CryptoHistoryRow,
    ArrowLeftIcon,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  onShow() { this.$options.hidden = false; this.liveRefresh(); },
  onHide() { this.$options.hidden = true; clearTimeout(this.$options.refreshTimer); },
  data() {
    return {
      historyState: undefined, // $STATE_LOADING, $STATE_LOADED, $STATE_ERROR,
      cursor: undefined,
      hasMore: true,
      transactions: [],
      price: undefined,
      indexing: undefined,
    };
  },
  computed: {
    isLoading() {
      return (!this.$account.capabilities.history && this.$walletState === this.$STATE_LOADING) || this.historyState === this.$STATE_LOADING;
    },
  },
  async mounted() {
    this.$account.market.getPrice(this.$wallet.crypto._id, this.$currency).then((price) => this.price = price);
    if (!this.$account.capabilities.history) await this.$loadWallet();
    this.loadMore();
  },
  methods: {
    async liveRefresh() {
      if (this.$options.hidden || !this.$account.capabilities.history) return;
      if (this.historyState === this.$STATE_LOADED) {
        try {
          const data = await this.$wallet.loadTransactions();
          if (!this.$options.hidden) {
            const current = new Map(this.transactions.map(item => [item.id, item]));
            for (const item of data.transactions) current.set(item.id, item);
            this.transactions = [...current.values()].sort((a,b) => b.timestamp-a.timestamp || b.id.localeCompare(a.id));
            this.indexing = data.indexing;
          }
        } catch { /* Retain the existing page while offline. */ }
      }
      if (!this.$options.hidden) this.$options.refreshTimer = setTimeout(() => this.liveRefresh(), 5000);
    },
    async refresh() {
      if (this.isLoading) return;
      this.historyState = undefined;
      this.cursor = undefined;
      this.hasMore = true;
      this.transactions = [];
      if (!this.$account.capabilities.history) await this.$loadWallet();
      this.loadMore();
    },
    async loadMore() {
      if (this.isLoading) return;
      if (!this.hasMore) return;
      if (this.historyState === this.$STATE_ERROR) return;
      await this.load();
    },
    async load() {
      if (!this.$account.capabilities.history && this.$walletState !== this.$STATE_LOADED) return;
      const { crypto } = this.$wallet;
      this.historyState = this.$STATE_LOADING;
      try {
        const [data] = await Promise.all([
          this.$wallet.loadTransactions({ cursor: this.cursor }),
          this.cursor === undefined && this.$account.capabilities.optionalProviders && this.$account.exchanges.loadExchanges(),
        ]);
        this.cursor = data.cursor;
        this.indexing = data.indexing;
        this.hasMore = data.hasMore;
        if (data.transactions && data.transactions.length) {
          const seen = new Set(this.transactions.map(item => item.id));
          const entries = this.$account.capabilities.optionalProviders ? this.$account.exchanges.exchangifyTransactions(data.transactions, crypto) : data.transactions;
          this.transactions.push(...entries.filter(item => !seen.has(item.id)));
        }
        this.historyState = this.$STATE_LOADED;
      } catch (err) {
        if (err.code === 'HISTORY_REFRESH_REQUIRED') {
          this.cursor = undefined; this.hasMore = true; this.transactions = [];
        }
        this.historyState = this.$STATE_ERROR;
      }
    },
  },
};
</script>

<template>
  <SmartLayout
    :isLoading="isLoading"
    :isLoadedAll="!hasMore"
    @loadMore="loadMore"
    @refresh="refresh"
  >
    <template #navbar="{ back }">
      <MwNavbar
        :title="$t('History')"
      >
        <template #left>
          <MwNavbarButton
            :title="$t('Back')"
            :aria-label="$t('Back')"
            @click="back"
          >
            <ArrowLeftIcon class="rtl-mirror" />
          </MwNavbarButton>
        </template>
        <template #right>
          <MwRefreshButton
            :isLoading="isLoading"
            @click="refresh"
          />
        </template>
      </MwNavbar>
    </template>
    <div class="&">
      <div v-if="indexing && (indexing.status !== 'caught_up' || !indexing.enabled)" class="&__empty">
        {{ $t('History is synchronizing. Earlier movements may still appear.') }}
      </div>
      <div
        v-if="!$account.capabilities.history && $walletState === $STATE_ERROR"
        class="&__error"
      >
        {{ $account.unknownError() }}
      </div>

      <div
        v-if="transactions.length"
        class="&__transactions"
      >
        <CryptoHistoryRow
          v-for="transaction in transactions"
          :key="transaction.id"
          :transaction="transaction"
          :price="price"
          class="&__transaction"
          @click="next('transaction', { transaction, price })"
        />
      </div>

      <div
        v-else-if="historyState === $STATE_LOADED && indexing?.status === 'caught_up'"
        class="&__empty"
      >
        {{ $t('You do not have any transactions yet.') }}
      </div>

      <div
        v-if="historyState === $STATE_ERROR"
        class="&__error"
      >
        {{ $account.unknownError() }}
      </div>

      <MwLoader
        v-if="isLoading"
      />
    </div>
  </SmartLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-md);

    &__empty,
    &__error {
      @include text-md;
    }

    &__transactions {
      display: flex;
      flex-direction: column;
      margin-right: calc(-1 * var(--spacing-sm));
      margin-left: calc(-1 * var(--spacing-sm));
      gap: var(--spacing-2xs);
    }
  }
</style>
