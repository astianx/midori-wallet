<script>
export default {
  props: { network: { type: Object, required: true } },
  computed: { colors() { return { base: '#0052ff', ethereum: '#5464aa', arbitrum: '#213147', polygon: '#8247e5', avalanche: '#e84142', optimism: '#d80032', tron: '#cf1236', solana: '#173a37', celo: '#477827' }[this.network.id] || 'var(--color-primary)'; } },
};
</script>
<template>
  <span class="&" :style="{ '--network-color': colors }" aria-hidden="true">
    <svg v-if="network.id === 'base'" viewBox="0 0 32 32" fill="none"><circle cx="16" cy="16" r="12" stroke="currentColor" stroke-width="3" /><path d="M4 16h24" stroke="currentColor" stroke-width="3" /></svg>
    <svg v-else-if="network.id === 'ethereum'" viewBox="0 0 32 32" fill="currentColor"><path d="m16 2-9 14 9 5 9-5Zm0 22-9-5 9 12 9-12Z" /></svg>
    <svg v-else-if="network.id === 'avalanche'" viewBox="0 0 32 32" fill="currentColor"><path d="M14 6c1-2 2-2 3 0l5 10c1 2 0 3-2 3H8c-2 0-3-1-2-3Zm10 16c1-2 2-2 3 0l2 4c1 2 0 3-2 3h-5c-2 0-3-1-2-3Z" /></svg>
    <span v-else>{{ network.id === 'optimism' ? 'OP' : network.name.slice(0, 2).toUpperCase() }}</span>
  </span>
</template>
<style lang="scss">
.#{ $filename } { display: grid; place-items: center; flex: none; width: 2.75rem; height: 2.75rem; border-radius: 50%; color: var(--network-color); background: color-mix(in srgb, var(--network-color) 10%, var(--color-background)); svg { width: 2rem; height: 2rem; } > span { font-size: 0.875rem; font-weight: 700; } }
</style>
