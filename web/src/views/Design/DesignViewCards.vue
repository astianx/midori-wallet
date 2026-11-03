<script>
import MwCard from '../../components/MwCard.vue';

export default {
  components: {
    MwCard,
  },
};
</script>

<template>
  <div class="&">
    <div class="&__column">
      <MwCard
        productId="aurum"
        size="small"
        lastFourDigits="8888"
      />
      <MwCard
        productId="aurum"
        size="medium"
        lastFourDigits="8888"
      />
      <MwCard
        productId="aurum"
        size="large"
        lastFourDigits="8888"
      />
      <MwCard
        productId="aurum"
        size="large"
        lastFourDigits="8888"
        locked
      />
    </div>
    <div class="&__column">
      <MwCard
        productId="azure"
        size="small"
        lastFourDigits="8888"
      />
      <MwCard
        productId="azure"
        lastFourDigits="8888"
        size="medium"
      />
      <MwCard
        productId="azure"
        lastFourDigits="8888"
        size="large"
      />
      <MwCard
        productId="azure"
        lastFourDigits="8888"
        size="large"
        locked
      />
    </div>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    display: flex;
    gap: var(--spacing-4xl);

    &__column {
      display: flex;
      width: 25rem;
      flex-direction: column;
      gap: var(--spacing-2xl);
    }
  }
</style>
