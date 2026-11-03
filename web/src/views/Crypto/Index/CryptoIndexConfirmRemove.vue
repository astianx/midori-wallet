<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwModal from '../../../components/MwModal.vue';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    MwModal,
  },
  props: {
    show: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['cancel', 'confirm'],
};
</script>

<template>
  <MwModal
    :show="show"
    :title="$t('Remove {symbol}', { symbol: $wallet.crypto.symbol })"
    @close="$emit('cancel')"
  >
    <div>
      {{ $t('Are you sure you want to remove {name} from the wallet?', { name: $wallet.crypto.name }) }}
    </div>
    <div v-if="$wallet.crypto.type === 'coin' && $account.tokensByPlatform($wallet.crypto.platform).length > 0">
      {{ $t('All tokens on the {name} platform will also be removed.', { name: $wallet.platform.name }) }}
    </div>
    <template #footer>
      <MwButtonGroup>
        <MwButton
          type="danger-light"
          @click="$emit('confirm')"
        >
          {{ $t('Remove') }}
        </MwButton>
        <MwButton
          type="primary-link"
          @click="$emit('cancel')"
        >
          {{ $t('Cancel') }}
        </MwButton>
      </MwButtonGroup>
    </template>
  </MwModal>
</template>
