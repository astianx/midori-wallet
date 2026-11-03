<script>
import { cryptoSubtitleWithSymbol } from '../../lib/helpers.js';

import MwButton from '../../components/MwButton.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../components/MwForm/MwFormTextareaReadonly.vue';
import MwTokenInfo from '../../components/MwTokenInfo.vue';
import MainLayout from '../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwButton,
    MwFormGroup,
    MwFormTextareaReadonly,
    MwTokenInfo,
  },
  data() {
    return {
      url: this.$wallet.tokenUrl,
      subtitleWithSymbol: cryptoSubtitleWithSymbol(this.$wallet),
    };
  },
};
</script>

<template>
  <MainLayout :title="$t('Token info')">
    <MwTokenInfo
      class="&__token-info"
      :crypto="$wallet.crypto"
      :platform="$wallet.platform"
      :title="$wallet.crypto.name"
      :subtitles="[subtitleWithSymbol, $t('{decimals} decimals', { decimals: $wallet.crypto.decimals })]"
    />
    <MwFormGroup>
      <MwFormTextareaReadonly
        :label="$t('Contract address')"
        :value="$wallet.crypto.address"
      />
      <MwButton
        v-if="url"
        type="primary-link"
        @click="$safeOpen(url)"
      >
        {{ $t('View in Block Explorer') }}
      </MwButton>
    </MwFormGroup>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__token-info {
      flex-grow: 1;
    }
  }
</style>
