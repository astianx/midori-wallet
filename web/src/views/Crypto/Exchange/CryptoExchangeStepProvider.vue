<script>
import MwProviderList from '../../../components/MwProviderList.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwProviderList,
  },
  extends: MwStep,
  data() {
    return {
    };
  },
  computed: {
    items() {
      return this.storage.estimations.map(({ provider, result }) => {
        const info = this.$account.exchanges.getProviderInfo(provider);
        return {
          ...info,
          description: `${result} ${this.storage.to.crypto.symbol}`,
        };
      });
    },
  },
  methods: {
    select(item) {
      this.updateStorage({
        provider: item.id,
      });
      this.back();
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Select provider')"
  >
    <MwProviderList
      :items="items"
      type="select"
      @click="select"
    />
  </MainLayout>
</template>
