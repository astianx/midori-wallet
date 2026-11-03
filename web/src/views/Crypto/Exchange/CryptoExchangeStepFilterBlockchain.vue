<script>
import MwCryptoList from '../../../components/MwCryptoList.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { SUPPORTED_PLATFORMS } from '../../../lib/constants.js';

export default {
  components: {
    MainLayout,
    MwCryptoList,
  },
  extends: MwStep,
  computed: {
    items() {
      return [{
        crypto: { _id: 'reset', type: 'custom' },
        title: this.$t('All blockchains'),
        subtitle: this.$t('Reset filter'),
      }, ...this.$account.cryptoDB
        .platforms(SUPPORTED_PLATFORMS)
        .map((crypto) => {
          return {
            crypto,
            title: crypto.name,
            subtitle: crypto.symbol,
          };
        })];
    },
  },
  methods: {
    select(id) {
      if (id === 'reset') {
        this.updateStorage({ filterPlatform: undefined });
      } else {
        this.updateStorage({ filterPlatform: id });
      }
      this.back();
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Choose a blockchain')"
    wide
  >
    <MwCryptoList
      :header="$t('Blockchains')"
      class="&__list"
      :items="items"
      :selected="storage.filterPlatform"
      columns
      @select="select"
    />
  </MainLayout>
</template>
