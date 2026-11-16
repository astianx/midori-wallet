<script>
import { fieldProps, useFormField } from './field.js';
import MwFormElement from './MwFormElement.vue';

import CopyIcon from '../../assets/svg/copy.svg';
import TickIcon from '../../assets/svg/tick.svg';

export default {
  setup(props) { return useFormField(props); },
  components: {
    MwFormElement,
    CopyIcon,
    TickIcon,
  },
  props: {
    ...fieldProps,
    value: {
      type: String,
      default: '',
    },
    copyValue: {
      type: String,
      default: undefined,
    },
    copy: {
      type: Boolean,
      default: false,
    },
    label: {
      type: String,
      default: '',
    },
    info: {
      type: [Boolean, String],
      default: false,
    },
    writable: {
      type: Boolean,
      default: false,
    },
    inheritDir: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      isCopied: false,
      copyError: false,
    };
  },
  beforeUnmount() { clearTimeout(this.copyTimer); },
  methods: {
    async onCopy() {
      this.copyError = false;
      try {
        await navigator.clipboard.writeText(this.copyValue || this.value);
        this.isCopied = true;
        clearTimeout(this.copyTimer);
        this.copyTimer = setTimeout(() => { this.isCopied = false; }, 1500);
      } catch { this.copyError = true; }
    },
  },
};
</script>

<template>
  <MwFormElement
    class="&"
    :class="{ '&--copyable': copy }"
    v-bind="$props"
    :controlId="controlId"
    :errorId="errorId"
    :error="copyError ? $t('Could not copy. Select the address and copy it manually.') : false"
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
    <div
      class="&__textarea"
      :id="controlId"
      :tabindex="disabled ? -1 : 0"
      :aria-disabled="disabled || undefined"
      role="textbox"
      aria-readonly="true"
      :aria-label="label || ariaLabel"
      :aria-describedby="copyError ? errorId : undefined"
      lang="en"
    >
      {{ value }}
    </div>
    <div
      v-if="$slots.after && copy === false"
      class="&__icon-after"
    >
      <slot name="after" />
    </div>
    <button v-else-if="copy" type="button" class="&__copy-button" :disabled="disabled" :aria-label="$t(isCopied ? 'Copied!' : 'Copy')" @click="onCopy"><TickIcon v-if="isCopied" aria-hidden="true" /><CopyIcon v-else aria-hidden="true" /></button>
    <span class="mw-sr-only" role="status">{{ isCopied ? $t('Copied!') : '' }}</span>

    <template #info>
      <slot name="info" />
    </template>
    <template
      v-if="$slots.infoFooter"
      #infoFooter
    >
      <slot name="infoFooter" />
    </template>
  </MwFormElement>
</template>

<style lang="scss">
  .#{ $filename } {
    &__textarea {
      @include text-md;
      min-width: 0;
      user-select: text;
      flex-grow: 1;
      padding: var(--spacing-sm) 0;
      border: none;
      background-color: transparent;
      color: var(--color-text);
      cursor: auto;
      font-family: inherit;
      line-height: inherit;
      overflow-wrap: anywhere;
    }

    &__icon-before,
    &__icon-after {
      width: var(--spacing-xl);
      height: var(--spacing-xl);
      flex-shrink: 0;
      align-self: start;
      margin: calc(1rem - 1px) 0;
      margin: calc(--spacing-md - 1px) 0;
    }

    &__copy-button { position: absolute; inset-inline-end: var(--space-2xs); top: var(--space-2xs); display: grid; place-items: center; align-self: flex-start; width: 2.75rem; height: 2.75rem; flex: none; border-radius: var(--radius-control); margin: 0; }
    &__copy-button svg { width: 1.25rem; height: 1.25rem; }

    &__copy {
      cursor: pointer;
    }

    &__success {
      [stroke] {
        stroke: var(--color-primary);
      }
    }
  }
.mw-form-textarea-readonly--copyable .mw-form-textarea-readonly__textarea { padding-inline-end: 2.75rem; }
</style>
