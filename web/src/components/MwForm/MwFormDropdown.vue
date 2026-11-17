<script>
import { fieldProps, useFormField } from './field.js';
import MwFormElement from './MwFormElement.vue';
import TrailingIcon from '../../assets/svg/trailing.svg';

export default {
  setup(props) { return useFormField(props); },
  components: {
    MwFormElement,
    TrailingIcon,
  },
  props: {
    ...fieldProps,
    value: {
      type: String,
      default: '',
    },
    label: {
      type: String,
      default: '',
    },
    error: {
      type: [String, Boolean],
      default: false,
    },
    info: {
      type: Boolean,
      default: false,
    },
    writable: {
      type: Boolean,
      default: true,
    },
  },
};
</script>

<template>
  <MwFormElement
    class="&"
    v-bind="$props"
    :controlId="controlId"
    :errorId="errorId"
    :disabled="disabled"
    :isLoading="isLoading"
    :success="success"
    :hint="hint"
  >
    <div
      v-if="$slots.before"
      class="&__icon-before"
    >
      <slot name="before" />
    </div>
    <div class="&__text">
      {{ value }}
      <button
        type="button"
        v-bind="fieldAttrs"
        :disabled="disabled || !writable"
        :aria-label="ariaLabel || `${label}: ${value}`"
        aria-haspopup="dialog"
        class="&__input"
        :class="{
          '&__input--writable': writable,
        }"
      ><span class="mw-sr-only">{{ value }}</span></button>
    </div>
    <div
      v-if="writable"
      class="&__icon-after"
    >
      <TrailingIcon />
    </div>
  </MwFormElement>
</template>

<style lang="scss">
  .#{ $filename } {
    &__text {
      @include ellipsis;
      flex-grow: 1;
    }

    &__input {
      @include transparent-stretch;
      cursor: default;

      &--writable {
        cursor: pointer;
      }
    }

    &__icon-before,
    &__icon-after {
      width: var(--spacing-xl);
      height: var(--spacing-xl);
      flex-shrink: 0;
    }
  }
</style>
