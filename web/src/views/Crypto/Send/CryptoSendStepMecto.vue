<script>
import MwMecto from '../../../components/MwMecto.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

export default {
  components: {
    MwMecto,
    MainLayout,
  },
  extends: MwStep,
  methods: {
    select(address) {
      this.updateStorage({ temp: { address } });
      this.back();
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Mecto')">
    <MwMecto
      @select="select"
    />
  </MainLayout>
</template>
