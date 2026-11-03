<script>
import MwButton from '../../components/MwButton.vue';
import MwButtonGroup from '../../components/MwButtonGroup.vue';

import PlusIcon from '../../assets/svg/plus.svg';
import SendIcon from '../../assets/svg/send.svg';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    PlusIcon,
    SendIcon,
  },
  data() {
    return {
      isLoading: {
        primary: false,
        secondary: false,
        primaryLight: false,
        dangerLight: false,
      },
    };
  },
  methods: {
    click(type) {
      this.isLoading[type] = true;
      setTimeout(() => {
        this.isLoading[type] = false;
      }, 3000);
    },
  },
};
</script>

<template>
  <div class="&">
    <MwButton
      type="primary"
      :isLoading="isLoading.primary"
      @click="click('primary')"
    >
      Primary
    </MwButton>

    <MwButton
      type="secondary"
      :isLoading="isLoading.secondary"
      @click="click('secondary')"
    >
      Secondary
    </MwButton>

    <MwButton
      type="primary-light"
      :isLoading="isLoading.primaryLight"
      @click="click('primaryLight')"
    >
      Primary light
    </MwButton>

    <MwButton
      type="danger-light"
      :isLoading="isLoading.dangerLight"
      @click="click('dangerLight')"
    >
      Danger light
    </MwButton>

    <MwButton type="primary-link">
      Primary link
    </MwButton>

    <MwButton type="danger-link">
      Danger link
    </MwButton>

    <MwButton
      type="primary"
      disabled
    >
      Disabled
    </MwButton>

    <MwButton
      type="primary"
      small
    >
      Small
    </MwButton>

    <MwButton type="primary-light">
      <PlusIcon />
      Icon + Text
    </MwButton>

    <MwButton
      type="primary-light"
      title="Icon button"
    >
      <PlusIcon />
    </MwButton>

    <MwButton class="&__custom-button">
      Custom
    </MwButton>

    <MwButton
      type="primary-link"
    >
      <PlusIcon />
      Icon + Text
    </MwButton>

    <MwButton
      small
      type="primary-link"
    >
      <PlusIcon />
      Icon + Text (Small)
    </MwButton>

    <MwButton
      class="&__custom-button"
      title="Custom icon button"
    >
      <PlusIcon />
    </MwButton>

    <MwButton
      class="&__multiline"
      type="primary-light"
    >
      <PlusIcon /> Maximum two lines here 😎 <PlusIcon />
    </MwButton>

    <MwButton
      type="primary-light"
      small
    >
      <PlusIcon /> Small + 2 Icons 😎 <PlusIcon />
    </MwButton>

    <MwButtonGroup type="circle">
      <MwButton
        type="circle"
      >
        <template #circle>
          <SendIcon />
        </template>
        Send
      </MwButton>
      <MwButton
        type="circle"
      >
        <template #circle>
          <SendIcon />
        </template>
        Exchange
      </MwButton>
      <MwButton
        type="circle"
      >
        <template #circle>
          <SendIcon />
        </template>
        Receive
      </MwButton>
      <MwButton
        type="circle"
      >
        <template #circle>
          <SendIcon />
        </template>
        History longwordheretitle
      </MwButton>
    </MwButtonGroup>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    .mw-button {
      margin-bottom: var(--spacing-xl);
    }

    &__custom-button {
      svg {
        width: 1.5rem;
        height: 1.5rem;
      }
    }

    &__multiline {
      width: 300px;
    }
  }
</style>
