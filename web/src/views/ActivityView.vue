<script>
import MainLayout from '../layouts/MainLayout.vue';
import MwActivityList from '../components/MwActivityList.vue';
export default { components: { MainLayout, MwActivityList }, data() { return { symbol: '' }; } };
</script>
<template>
  <MainLayout :title="$t('Activity')" wide allowReadFailure>
    <div class="&__filters" :aria-label="$t('Stablecoins')"><button v-for="value in ['', 'USDC', 'USDT']" :key="value" type="button" :aria-pressed="symbol === value" :class="{ '&__filter--active': symbol === value }" @click="symbol = value">{{ value || $t('All') }}</button></div>
    <MwActivityList :symbol="symbol" />
  </MainLayout>
</template>
<style lang="scss">
.#{ $filename }__filters { display: flex; gap: var(--space-xs); }
.#{ $filename }__filters button { min-height: 2.75rem; padding: var(--space-xs) var(--space-lg); font-size: 0.875rem; border: 1px solid var(--color-rule); border-radius: var(--radius-control); color: var(--color-muted); white-space: nowrap; }
.#{ $filename }__filter--active { background: var(--color-surface-tint); color: var(--color-accent) !important; border-color: var(--color-accent) !important; }
</style>
