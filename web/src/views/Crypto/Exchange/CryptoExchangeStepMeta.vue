<script>
import { errors } from '../../../lib/account/domain.js';

import MwButton from '../../../components/MwButton.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

import { cryptoSubtitle } from '../../../lib/helpers.js';
import { onShowOnHide } from '../../../lib/mixins.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormGroup,
    MwFormInput,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  onShow() {
    if (this.storage.temp?.meta?.destinationTag) {
      this.extraId = this.storage.temp.meta.destinationTag;
      this.storage.temp.meta.destinationTag = undefined;
    }
  },
  data() {
    return {
      isLoading: false,
      subtitle: cryptoSubtitle(this.$wallet),
      error: undefined,
      extraId: undefined,
    };
  },
  methods: {
    async confirm() {
      this.isLoading = true;
      this.error = undefined;
      try {
        await this.$account.exchanges.validateAddress({
          to: this.storage.to.crypto._id,
          address: this.storage.address,
          extraId: this.extraId,
          provider: this.storage.provider,
        });
        this.updateStorage({
          extraId: this.extraId,
        });
        this.next('confirm');
      } catch (err) {
        if (err instanceof errors.EmptyAddressError) {
          this.error = this.$t('Invalid extra ID');
          return;
        }
        if (err instanceof errors.InvalidAddressError) {
          this.error = this.$t('Invalid extra ID');
          return;
        }
        console.error(err);
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Swap {symbol}', { symbol: $wallet.crypto.symbol })"
    :description="subtitle"
  >
    <MwFormGroup class="&__container">
      <MwFormInput
        v-model="extraId"
        :label="$t('Extra ID')"
        :placeholder="$t('(optional)')"
        :error="error"
        @update:modelValue="error = undefined"
      />
    </MwFormGroup>
    <MwButton
      type="primary"
      :isLoading="isLoading"
      @click="confirm"
    >
      {{ $t('Continue') }}
    </MwButton>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__container {
      flex-grow: 1;
    }
  }
</style>
