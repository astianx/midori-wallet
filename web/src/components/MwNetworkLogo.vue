<script>
import base from '../assets/networks/base.svg?url';
import ethereum from '../assets/networks/ethereum.svg?url';
import arbitrum from '../assets/networks/arbitrum.png';
import polygon from '../assets/networks/polygon.svg?url';
import avalanche from '../assets/networks/avalanche.svg?url';
const logos = { base, ethereum, arbitrum, polygon, avalanche };
export default {
  props: { network: { type: Object, required: true } },
  computed: { logo() { return logos[this.network.id]; } },
};
</script>
<template>
  <span class="&" :class="`&--${network.id}`" aria-hidden="true"><img :src="logo" alt="" /></span>
</template>
<style lang="scss">
.#{ $filename } {
  display: grid; place-items: center; flex: none; width: 2.75rem; height: 2.75rem;
  img { display: block; width: 2rem; height: 2rem; object-fit: contain; }
  &--arbitrum img { width: 3.5rem; height: 3.5rem; max-width: none; }
}
</style>
