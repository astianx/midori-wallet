<script>
import MwButton from '../../components/MwButton.vue';
import MwCard from '../../components/MwCard.vue';
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
    MwButton,
    MwCard,
    MwNavbar,
    MwNavbarButton,
    MwRefreshButton,
    ArrowLeftIcon,
  },
  extends: MwStep,
  data() {
    const { $t } = this;
    const statusLabel = {
      pending: $t('Pending'),
      kyc: $t('Start KYC'),
      pending_kyc: $t('Pending KYC'),
      issuing: $t('Issuing'),
      failed_kyc: $t('Failed KYC'),
      failed: $t('Failed'),
      locked: $t('Locked'),
    };
    return {
      isLoading: true,
      isBuying: false,
      cards: [],
      error: undefined,
      statusLabel,
    };
  },
  async mounted() {
    this.load();
  },
  methods: {
    async refresh() {
      if (this.isLoading) return;
      this.cards = [];
      this.error = undefined;
      this.load();
    },
    async load() {
      this.isLoading = true;
      try {
        this.cards = await this.$account.cards.list();
        this.updateStorage({
          priceCard: await this.$account.market.getPrice('tether@ethereum', this.$currency),
        });
      } catch (err) {
        this.error = this.$account.unknownError(err);
        console.error(err);
      } finally {
        this.isLoading = false;
      }
    },
    select(card, index) {
      if (!['active', 'locked', 'kyc'].includes(card.status)) return;
      if (card.status === 'kyc') {
        this.updateStorage({ kyc: {} });
        return this.next('kycPersonal');
      }
      this.updateStorage({
        card,
        updateCard: (card) => this.cards[index] = card,
      });
      this.next('card');
    },
    async buy() {
      this.isBuying = true;
      const countryCode = await this.$account.cards.getCountryCode();
      this.next('chooseCountry', { countryCode });
      this.isBuying = false;
    },
  },
};
</script>

<template>
  <SmartLayout
    :isLoading="isLoading"
    @refresh="refresh"
  >
    <template #navbar="{ back }">
      <MwNavbar
        :title="$t('Cards')"
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
    <MwLoader v-if="isLoading" />
    <div
      v-else-if="error"
      class="&__error"
    >
      {{ error }}
    </div>
    <template v-else>
      <div class="&__cards">
        <div v-if="cards.length">
          <div class="&__list">
            <div
              v-for="(card, index) in cards"
              :key="card.id"
              class="&__row"
              :class="{ '&__row--ready': ['active', 'locked', 'kyc'].includes(card.status) }"
              @click="select(card, index)"
            >
              <MwCard
                class="&__card"
                :productId="card.product?.id"
                :lastFourDigits="card.lastFourDigits"
                size="small"
              />
              <div class="&__content">
                <div class="&__title">
                  {{ card.product?.name }}
                </div>
                <div class="&__description">
                  <span
                    v-if="card.status === 'active'"
                    dir="ltr"
                  >
                    {{ $isHiddenBalance ? '*****' : `${card.balance} ${card.symbol}` }}
                  </span>
                  <span v-else>
                    {{ statusLabel[card.status] }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else>
          {{ $t('You do not have any cards yet.') }}
        </div>
      </div>
      <MwButton
        v-if="cards.length < 20"
        :isLoading="isBuying"
        type="primary"
        @click="buy"
      >
        {{ $t('Buy card') }}
      </MwButton>
    </template>
  </SmartLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__cards {
      @include text-md;
      flex-grow: 1;
    }

    &__list {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-3xl);
    }

    &__row {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);

      &--ready {
        cursor: pointer;
      }
    }

    &__card {
      flex-shrink: 0;
    }

    &__content {
      display: flex;
      overflow: hidden;
      flex-direction: column;
    }

    &__title {
      @include text-bold;
    }

    &__description {
      @include text-xs;
      @include ellipsis;
      color: var(--color-secondary);
    }

    &__error {
      @include text-md;
    }
  }
</style>
