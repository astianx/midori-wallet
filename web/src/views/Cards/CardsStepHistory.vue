<script>
import CardsHistoryRow from './CardsHistoryRow.vue';
import MwLoader from '../../components/MwLoader.vue';
import MwNavbar from '../../components/MwNavbar.vue';
import MwNavbarButton from '../../components/MwNavbarButton.vue';
import MwRefreshButton from '../../components/MwRefreshButton.vue';
import MwStep from '../../components/MwStep.vue';
import SmartLayout from '../../layouts/SmartLayout.vue';

import ArrowLeftIcon from '../../assets/svg/arrowLeft.svg';

export default {
  components: {
    SmartLayout,
    MwLoader,
    MwNavbarButton,
    MwRefreshButton,
    MwNavbar,
    CardsHistoryRow,
    ArrowLeftIcon,
  },
  extends: MwStep,
  data() {
    return {
      isLoading: false,
      error: undefined,
      cursor: undefined,
      hasMore: true,
      transactions: [],
    };
  },
  async mounted() {
    this.loadMore();
  },
  methods: {
    async refresh() {
      if (this.isLoading) return;
      this.error = undefined;
      this.cursor = undefined;
      this.hasMore = true;
      this.transactions = [];
      this.loadMore();
    },
    async loadMore() {
      if (this.isLoading) return;
      if (!this.hasMore) return;
      if (this.error) return;
      await this.load();
    },
    async load() {
      this.isLoading = true;
      try {
        const data = await this.$account.cards.loadTransactions(this.storage.card, this.cursor);
        this.updateStorage({
          priceCard: await this.$account.market.getPrice('tether@ethereum', this.$currency),
        });
        this.cursor = data.cursor;
        this.hasMore = data.hasMore;
        if (data.transactions && data.transactions.length) {
          this.transactions.push(...data.transactions.filter((item) => !item.hidden));
        }
      } catch (err) {
        console.error('Midori Wallet operation failed');
        this.error = err;
      } finally {
        this.isLoading = false;
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
      <MwNavbar :title="$t('History')">
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
      <div
        v-if="transactions.length"
        class="&__transactions"
      >
        <CardsHistoryRow
          v-for="transaction in transactions"
          :key="transaction.id"
          :transaction="transaction"
          :price="storage.priceCard"
          class="&__transaction"
          @click="next('transaction', { price: storage.priceCard, transaction })"
        />
      </div>

      <div
        v-else-if="!isLoading && !error"
        class="&__empty"
      >
        {{ $t('You do not have any transactions yet.') }}
      </div>

      <div
        v-if="error"
        class="&__error"
      >
        {{ $account.unknownError(error) }}
      </div>

      <MwLoader v-if="isLoading" />
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
