<script>
import MwBigIntInput from '../MwBigIntInput.vue';
import MwFormElement from './MwFormElement.vue';

export default {
  components: {
    MwBigIntInput,
    MwFormElement,
  },
  props: {
    modelValue: {
      type: BigInt,
      default: undefined,
    },
    label: {
      type: String,
      default: undefined,
    },
    error: {
      type: [Boolean, String],
      default: false,
    },
    small: {
      type: Boolean,
      default: false,
    },
    info: {
      type: [Boolean, String],
      default: false,
    },
  },
  emits: ['update:modelValue'],
  computed: {
    inputValue: {
      get() {
        return this.modelValue;
      },
      set(value) {
        this.$emit('update:modelValue', value);
      },
    },
  },
};
</script>

<template>
  <MwFormElement
    class="&"
    :label="label"
    :error="error"
    :small="small"
    :info="info"
  >
    <MwBigIntInput
      v-model="inputValue"
      class="&__amount"
    />
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
    &__amount {
      @include ellipsis;
      flex-grow: 1;
    }
  }
</style>
