<script>
import MwButton from '../components/MwButton.vue';
import MwSwitch from '../components/MwSwitch.vue';

export default {
  components: {
    MwButton,
    MwSwitch,
  },
  data() {
    return {
      theme: document.documentElement.dataset.theme,
    };
  },
  methods: {
    switchTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light';
      document.documentElement.dataset.theme = this.theme;
      localStorage.setItem('_cs_theme', this.theme);
    },
  },
};
</script>

<template>
  <div class="&">
    <div class="&__container">
      <MwButton
        class="&__back"
        @click="$router.up()"
      >
        Back
      </MwButton>

      <div class="&__switch">
        Theme ({{ theme }}):
        <MwSwitch
          :checked="theme === 'dark'"
          @click="switchTheme"
        />
      </div>
    </div>
    <RouterView />
  </div>
</template>

<style lang="scss">
  .#{ $filename} {
    height: 100%;
    padding: var(--spacing-xl);
    background-color: var(--color-background);
    overflow-y: auto;

    &__container {
      display: flex;
      align-items: center;
      margin-bottom: var(--spacing-lg);
      gap: var(--spacing-xl);
    }

    &__switch {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
    }

    &__back {
      display: block;
    }
  }
</style>
