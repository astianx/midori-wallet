<script>
import MwErrorHardwareNotSupported from './components/MwErrorHardwareNotSupported.vue';
import MwModalUseHardwareKey from './components/MwModalUseHardwareKey.vue';
import MwUpdater from './components/MwUpdater.vue';
import { RouterView } from 'vue-router';

export default {
  components: {
    RouterView,
    MwErrorHardwareNotSupported,
    MwModalUseHardwareKey,
    MwUpdater,
  },
  mounted() {
    if (this.env.VITE_BUILD_TYPE === 'phonegap') {
      window.navigator.splashscreen?.hide();
    }
  },
};
</script>

<template>
  <RouterView />
  <MwUpdater />
  <MwErrorHardwareNotSupported />
  <MwModalUseHardwareKey />
</template>

<style lang="scss">
  #app {
    width: 100%;
    height: 100%;
  }
</style>
