<script>
import MwButton from '../../components/MwButton.vue';
import MwStep from '../../components/MwStep.vue';
import MainLayout from '../../layouts/MainLayout.vue';
import { walletSeed } from '../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
  },
  extends: MwStep,
  mixins: [walletSeed],
  data() {
    return {
      isLoading: false,
    };
  },
  methods: {
    async remove() {
      this.isLoading = true;
      await this.walletSeed(async () => {
        await this.$account.cards.remove(this.storage.card.id);
        this.$router.replace({ name: 'cards', force: true });
      });
      this.isLoading = false;
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Remove card')"
  >
    <div
      class="&__container"
    >
      <div class="&__note">
        {{ $t('Are you sure you want to remove your card? You cannot undo this action.') }}
      </div>
    </div>
    <MwButton
      type="danger-light"
      :isLoading="isLoading"
      @click="remove"
    >
      {{ $t('Remove card') }}
    </MwButton>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      flex-grow: 1;
    }

    &__note {
      @include text-md;
    }
  }
</style>
