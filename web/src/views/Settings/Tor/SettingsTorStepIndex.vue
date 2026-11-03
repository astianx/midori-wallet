<script>
import MwListItem from '../../../components/MwListItem.vue';
import MwListItems from '../../../components/MwListItems.vue';
import MwStep from '../../../components/MwStep.vue';
import MwSwitch from '../../../components/MwSwitch.vue';
import MainLayout from '../../../layouts/MainLayout.vue';

export default {
  components: {
    MainLayout,
    MwListItem,
    MwListItems,
    MwSwitch,
  },
  extends: MwStep,
  data() {
    return {
      isLoading: false,
    };
  },
  methods: {
    toggleOnion() {
      this.isLoading = true;
      try {
        this.$account.toggleOnion();
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Tor')"
  >
    <MwListItems>
      <MwListItem
        :title="$t('Tor')"
        :description="$t('Route traffic through Tor network. Ensure that your Tor VPN is active.')"
        :arrow="false"
      >
        <template #after>
          <MwSwitch
            :checked="$isOnion"
            :isLoading="isLoading"
            :aria-label="$t('Route traffic through Tor network. Ensure that your Tor VPN is active.')"
            @click="toggleOnion"
          />
        </template>
      </MwListItem>
    </MwListItems>
  </MainLayout>
</template>
