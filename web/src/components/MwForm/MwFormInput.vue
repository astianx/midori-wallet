<script>
import { fieldProps, useFormField } from './field.js';
import MwFormElement from './MwFormElement.vue';

import CloseIcon from '../../assets/svg/close.svg';

export default {
  setup(props) { return useFormField(props); },
  components: {
    MwFormElement,
    CloseIcon,
  },
  props: {
    ...fieldProps,
    readonly: { type: Boolean, default: false },
    modelValue: {
      type: String,
      default: '',
    },
    label: {
      type: String,
      default: undefined,
    },
    error: {
      type: [String, Boolean],
      default: false,
    },
    placeholder: {
      type: String,
      default: '',
    },
    small: {
      type: Boolean,
      default: false,
    },
    clear: {
      type: Boolean,
      default: false,
    },
    info: {
      type: [Boolean, String],
      default: false,
    },
    inputmode: {
      type: String,
      default: 'text',
    },
  },
  emits: ['update:modelValue'],
};
</script>

<template>
  <MwFormElement
    class="&"
    :class="{ '&--clearable': clear }"
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
    <input
      v-bind="fieldAttrs"
      :value="modelValue"
      :readonly="readonly"
      type="text"
      :inputmode="inputmode"
      dir="ltr"
      lang="en"
      class="&__input"
      autocorrect="off"
      autocapitalize="off"
      autocomplete="off"
      spellcheck="false"
      :placeholder="placeholder"
      @input="$emit('update:modelValue', $event.target.value.trim())"
    >
    <div
      v-if="$slots.after"
      class="&__icon-after"
    >
      <slot name="after" />
    </div>
    <button
      type="button"
      v-if="clear && modelValue && !readonly"
      class="&__icon-after"
      :title="$t('Clear')"
      :aria-label="$t('Clear')"
      :disabled="disabled"
      @click.prevent="$emit('update:modelValue', '')"
    >
      <CloseIcon aria-hidden="true" />
    </button>
    <template #info>
      <slot name="info" />
    </template>
    <template
      v-if="$slots.infoFooter"
      #infoFooter
    >
      <slot name="infoFooter" />
    </template>
    <template
      v-if="$slots.button"
      #button
    >
      <slot name="button" />
    </template>
  </MwFormElement>
</template>

<style lang="scss">
  .#{ $filename } {
    &__input {
      @include ellipsis;
      width: 100%;
      flex-grow: 1;

      &::placeholder {
        color: var(--color-secondary);
      }

      [dir="rtl"] & {
        text-align: end;
      }
    }

    &__icon-before,
    &__icon-after {
      width: var(--spacing-xl);
      height: var(--spacing-xl);
      flex-shrink: 0;
    }
  }
.mw-form-input--clearable .mw-form-input__input { padding-inline-end: 2.75rem; }
button.mw-form-input__icon-after { position: absolute; inset-inline-end: var(--space-2xs); top: 50%; transform: translateY(-50%); display: grid; place-items: center; width: 2.75rem; height: 2.75rem; }
button.mw-form-input__icon-after svg { width: 1.25rem; height: 1.25rem; }
</style>
