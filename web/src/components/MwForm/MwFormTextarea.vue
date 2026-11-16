<script>
import { fieldProps, useFormField } from './field.js';
import MwFormElement from './MwFormElement.vue';

export default {
  setup(props) { return useFormField(props); },
  components: {
    MwFormElement,
  },
  props: {
    ...fieldProps,
    modelValue: {
      type: String,
      default: '',
    },
    label: {
      type: String,
      default: undefined,
    },
    rows: {
      type: Number,
      default: 3,
    },
    error: {
      type: [String, Boolean],
      default: false,
    },
  },
  emits: ['update:modelValue'],
  methods: {
    focus() {
      this.$refs.input.focus();
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
    <textarea
      v-bind="fieldAttrs"
      ref="input"
      dir="ltr"
      lang="en"
      :value="modelValue"
      class="&__textarea"
      :rows="rows"
      autocorrect="off"
      autocapitalize="off"
      autocomplete="off"
      spellcheck="false"
      @input="$emit('update:modelValue', $event.target.value.trim())"
    />
  </MwFormElement>
</template>

<style lang="scss">
  .#{ $filename } {
    &__textarea {
      @include text-md;
      flex-grow: 1;
      padding: var(--spacing-sm) 0;
      border: none;
      background-color: transparent;
      color: var(--color-text);
      font-family: inherit;
      line-height: inherit;
      min-height: 6rem;
      resize: vertical;

      [dir="rtl"] & {
        text-align: end;
      }
    }
  }
</style>
