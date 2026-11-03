<script>
import MwButton from '../components/MwButton.vue';
import MwButtonGroup from '../components/MwButtonGroup.vue';
import MwModal from '../components/MwModal.vue';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    MwModal,
  },
  data() {
    return {
      show: false,
      updateInfo: undefined,
    };
  },
  methods: {
    async checkUpdate() { return 'disabled'; },
    update() {
      if (!this.updateInfo) return;
      if (this.env.VITE_BUILD_TYPE === 'web') return location.reload();
      this.$safeOpen(this.updateInfo.url, '_blank');
    },
  },
};
</script>

<template>
  <MwModal
    :show="show"
    :title="$t('Update')"
    @close="show = false"
  >
    {{ $t('A new version of Midori Wallet is available. Would you like to update it now?') }}
    <template #footer>
      <MwButtonGroup>
        <MwButton
          type="primary"
          @click="update"
        >
          {{ $t('Update') }}
        </MwButton>
        <MwButton
          type="primary-link"
          @click="show = false"
        >
          {{ $t('Skip') }}
        </MwButton>
      </MwButtonGroup>
    </template>
  </MwModal>
</template>
