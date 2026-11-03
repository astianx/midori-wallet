<script>
import MwButton from '../../components/MwButton.vue';
import MwFormSelect from '../../components/MwForm/MwFormSelect.vue';
import MwStep from '../../components/MwStep.vue';
import MainLayout from '../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwFormSelect,
    MwButton,
  },
  extends: MwStep,
  data() {
    return {
      countryCode: this.args.countryCode,
      countries: this.$account.cards.countries,
      error: undefined,
    };
  },
  methods: {
    select() {
      this.error = undefined;
      this.$account.cards.setCountryCode(this.countryCode);
    },
    confirm() {
      const products = this.$account.cards.getProducts(this.countryCode);
      if (products.length) {
        this.next('showcase', { products });
      } else {
        this.error = this.$t('This country/region is currently not supported');
      }
    },
  },
};
</script>

<template>
  <MainLayout :title="$t('Buy card')">
    <MwFormSelect
      v-model="countryCode"
      class="&__select"
      :label="$t('Select your country/region of residence')"
      :options="countries"
      :error="error"
      @update:modelValue="select"
    />
    <MwButton
      type="primary"
      @click="confirm"
    >
      {{ $t('Continue') }}
    </MwButton>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__select {
      flex-grow: 1;
    }
  }
</style>
