<script>
import { stablecoinFamily, groupStablecoins } from '../lib/account/stablecoin-accounts.js';
export default {
  computed: {
    family() { return stablecoinFamily(this.$wallet.crypto); },
    account() { return groupStablecoins(this.$cryptos, this.$wallet.crypto._id).find(item => item.family === this.family); },
  },
};
</script>
<template>
  <dl v-if="account" class="&" :aria-label="$t('Network')">
    <div v-for="position in account.positions" :key="position.crypto._id"><dt>{{ position.platform.name }}<small v-if="position.crypto.platform === 'base' && family === 'USDT'"> · {{ $t('Bridged USDT') }}</small></dt><dd dir="ltr">{{ $isHiddenBalance ? '••••' : position.balance?.toString() ?? '—' }} {{ position.crypto.symbol }}</dd></div>
  </dl>
</template>
<style lang="scss">
.#{ $filename } {
  display: grid; gap: var(--spacing-sm); margin: var(--spacing-md) 0 0; font-size: 0.8125rem;
  > div { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--spacing-sm); }
  dt { color: var(--color-secondary); } dd { margin: 0; overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
}
</style>
