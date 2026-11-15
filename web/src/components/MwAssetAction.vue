<script>
import MwButton from './MwButton.vue';
import MwModal from './MwModal.vue';
import MwAppIcon from './MwAppIcon.vue';
import MwCryptoLogo from './MwCryptoLogo.vue';
export default {
  components: { MwButton, MwModal, MwAppIcon, MwCryptoLogo },
  props: { action: { type: String, default: 'send' }, label: { type: String, required: true }, icon: { type: String, default: 'send' }, primary: { type: Boolean, default: false }, disabled: { type: Boolean, default: false } },
  data() { return { open: false }; },
  methods: { choose(id) { this.open = false; this.$router.push({ name: `crypto.${this.action}`, params: { cryptoId: id } }); } },
};
</script>
<template>
  <MwButton :type="primary ? 'primary' : 'secondary'" :disabled="disabled" @click="open = true"><MwAppIcon :name="icon" />{{ label }}</MwButton>
  <MwModal :show="open" :title="label" @close="open = false">
    <button v-for="item in $cryptos" :key="item.crypto._id" class="&__asset" type="button" @click="choose(item.crypto._id)"><MwCryptoLogo :crypto="item.crypto" :platform="item.platform" /><span>{{ item.crypto.symbol }}<small>Base</small></span><MwAppIcon name="arrow" /></button>
  </MwModal>
</template>
<style lang="scss">
.#{ $filename }__asset { display: flex; width: 100%; align-items: center; gap: var(--space-md); min-height: 4rem; padding: var(--space-sm); border-radius: var(--radius-control); text-align: start; background: var(--color-surface-subtle); color: var(--color-ink); }
.#{ $filename }__asset > .mw-crypto-logo { width: 2.5rem; height: 2.5rem; }
.#{ $filename }__asset > span { flex: 1; font-weight: 600; }
.#{ $filename }__asset small { display: block; font-weight: 400; color: var(--color-muted); }
.#{ $filename }__asset > svg:last-child { width: 1.25rem; height: 1.25rem; }
</style>
