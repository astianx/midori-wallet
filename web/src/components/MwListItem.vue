<script>
import { computed, provide, useId } from 'vue';
import ChevronRightIcon from '../assets/svg/chevronRight.svg';

export default {
  setup(props) { const titleId = `list-title-${useId()}`; provide('mw-list-item-title', computed(() => props.title ? titleId : undefined)); return { titleId }; },
  computed: { interactive() { return Boolean(this.$.vnode.props?.onClick) && !this.$slots.after; } },
  components: {
    ChevronRightIcon,
  },
  props: {
    title: {
      type: String,
      default: '',
    },
    description: {
      type: String,
      default: '',
    },
    arrow: {
      type: Boolean,
      default: true,
    },
    type: {
      type: String,
      default: '',
    },
    disabled: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['click'],
};
</script>

<template>
  <li
    class="&"
    :class="{
      '&--clickable': interactive,
      '&--disabled': disabled,
      [`&--${type}`]: type,
    }"
  >
    <component :is="interactive ? 'button' : 'div'" class="&__control" :type="interactive ? 'button' : undefined" :disabled="interactive ? disabled : undefined" :inert="!interactive && disabled ? true : undefined" :aria-disabled="disabled || undefined" @click="interactive && !disabled && $emit('click')">
    <div
      v-if="$slots.before"
      class="&__before"
    >
      <slot name="before" />
    </div>

    <div class="&__content">
      <slot />
      <div
        v-if="title"
        :id="titleId"
        class="&__title"
      >
        {{ title }}
      </div>
      <div
        v-if="description"
        class="&__description"
      >
        {{ description }}
      </div>
    </div>

    <div
      v-if="$slots.after || arrow"
      class="&__after"
    >
      <slot
        v-if="$slots.after"
        name="after"
      />
      <ChevronRightIcon
        v-else
        class="rtl-mirror"
        aria-hidden="true"
      />
    </div>
    </component>
  </li>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;
    min-height: 3.5rem;
    &__control {
      display: flex; width: 100%; min-height: 3.5rem; text-align: start;
    flex-direction: row;
    align-items: center;
    padding-right: var(--spacing-xl);
    padding-left: var(--spacing-xl);
    gap: var(--spacing-xs);

    @include breakpoint(lg) {
      padding-right: 0;
      padding-left: 0;
    }

    }

    &__content {
      min-width: 20%;
      flex-grow: 1;
      padding-top: var(--spacing-sm);
      padding-bottom: var(--spacing-sm);
    }

    &__title {
      @include text-md;
      @include ellipsis;
    }

    &__description {
      @include text-sm;
      color: var(--color-secondary);
    }

    &__before,
    &__after {
      button {
        width: var(--spacing-xl);
        height: var(--spacing-xl);
      }

      svg {
        width: var(--spacing-xl);
        height: var(--spacing-xl);
      }
    }

    &--clickable {
      cursor: pointer;
    }

    &--disabled {
      opacity: 0.4;
      pointer-events: none;
    }

    &--danger {
      color: var(--color-danger);
    }
  }
</style>
