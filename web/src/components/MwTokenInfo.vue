<script>
import MwCryptoLogo from '../components/MwCryptoLogo.vue';

export default {
  components: {
    MwCryptoLogo,
  },
  props: {
    crypto: {
      type: Object,
      required: true,
    },
    platform: {
      type: Object,
      default: undefined,
    },
    title: {
      type: String,
      required: true,
    },
    subtitles: {
      type: Array,
      default() {
        return [];
      },
    },
  },
};
</script>

<template>
  <div class="&">
    <MwCryptoLogo
      class="&__icon"
      :crypto="crypto"
      :platform="platform"
    />
    <div
      class="&__title"
      dir="ltr"
    >
      {{ title }}
    </div>
    <div
      v-for="subtitle in subtitles"
      :key="subtitle"
      class="&__subtitle"
      dir="ltr"
    >
      {{ subtitle }}
    </div>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--spacing-2xs);
    text-align: center;

    &__icon {
      width: var(--spacing-5xl);
      height: var(--spacing-5xl);
      margin-bottom: var(--spacing-sm);
    }

    &__title {
      @include text-md;
      @include text-bold;
      overflow-wrap: anywhere;
    }

    &__subtitle {
      @include text-sm;
      overflow-wrap: anywhere;
    }
  }
</style>
