<script>
import MwLoader from './MwLoader.vue';
import TickIcon from '../assets/svg/tick.svg';
import DangerTriangleIcon from '../assets/svg/dangerTriangle.svg';

export default {
  components: {
    MwLoader, TickIcon, DangerTriangleIcon,
  },
  props: {
    state: { type: String, default: '', validator: value => ['', 'error', 'success'].includes(value) },
    type: {
      type: String,
      default: '',
    },
    size: {
      type: String,
      default: '',
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    small: {
      type: Boolean,
      default: false,
    },
    isLoading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['click'],
};
</script>

<template>
  <button
    type="button"
    class="&"
    :class="{
      [`&--${type}`]: type,
      '&--small': small,
      '&--loading': isLoading,
      '&--error': state === 'error',
      '&--success': state === 'success',
    }"
    :disabled="disabled || isLoading"
    :aria-busy="isLoading"
    :aria-label="$attrs['aria-label'] || $attrs.title || undefined"
    @click="!isLoading && !disabled && $emit('click')"
  >
    <MwLoader v-if="isLoading" :type="type" class="&__spinner" aria-hidden="true" />
    <TickIcon v-else-if="state === 'success'" aria-hidden="true" />
    <DangerTriangleIcon v-else-if="state === 'error'" aria-hidden="true" />
      <div
        v-if="$slots.circle"
        class="&__circle"
      >
        <slot name="circle" />
      </div>
      <slot />
  </button>
</template>

<style lang="scss">
  %base-button {
    display: flex;
    position: relative;
    overflow: hidden;
    height: 3.5rem;
    align-items: center;
    justify-content: center;
    padding: 0 var(--spacing-lg);
    border: 1px solid transparent;
    border-radius: 0.625rem;
    gap: var(--spacing-md);
    line-height: 1.2;
    transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;

    @include text-md;
    @include text-bold;

    &:disabled {
      opacity: 0.4;
    }

    svg {
      width: var(--spacing-xl);
      height: var(--spacing-xl);
      flex-shrink: 0;

      [stroke] {
        stroke: var(--color-text);
      }

      [fill] {
        fill: var(--color-text);
      }
    }
  }
  .#{ $filename } {
    $self: &;

    &:disabled {
      cursor: not-allowed;
    }

    &__circle {
      display: flex;
      width: 3.5rem;
      height: 3.5rem;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      background-color: var(--color-secondary-light);

      @include hover {
        background-color: var(--color-secondary-light-d-5);
      }

      &:active {
        background-color: var(--color-secondary-light-d-10);
      }
    }

    &--base {
      @extend %base-button;
    }

    &--primary {
      @extend %base-button;
      background-color: var(--color-primary-brand);
      color: var(--color-primary-button-text);

      @include hover {
        background-color: var(--color-primary-brand-d-10);
      }

      &:active {
        background-color: var(--color-primary-brand-d-15);
      }
    }

    &--secondary {
      @extend %base-button;
      background-color: var(--color-secondary-light);

      @include hover {
        background-color: var(--color-secondary-light-d-5);
      }

      &:active {
        background-color: var(--color-secondary-light-d-7);
      }
    }

    &--primary-light {
      @extend %base-button;
      background-color: var(--color-primary-light);
      color: var(--color-primary);

      @include hover {
        background-color: var(--color-primary-light-d-5);
      }

      &:active {
        background-color: var(--color-primary-light-d-7);
      }

      svg {
        [stroke] {
          stroke: var(--color-primary);
        }

        [fill] {
          fill: var(--color-primary);
        }
      }
    }

    &--danger-light {
      @extend %base-button;
      background-color: var(--color-danger-light);
      color: var(--color-danger);

      @include hover {
        background-color: var(--color-danger-light-d-5);
      }

      &:active {
        background-color: var(--color-danger-light-d-7);
      }

      svg {
        [stroke] {
          stroke: var(--color-danger);
        }

        [fill] {
          fill: var(--color-danger);
        }
      }
    }

    &--primary-link {
      @extend %base-button;
      background-color: transparent;
      color: var(--color-primary);

      @include hover {
        color: var(--color-primary-d-10);
      }

      &:active {
        color: var(--color-primary-d-15);
      }

      svg {
        [stroke] {
          stroke: var(--color-primary);
        }

        [fill] {
          fill: var(--color-primary);
        }
      }
    }

    &--white-link {
      @extend %base-button;
      background-color: transparent;
      color: var(--color-white);

      @include hover {
        color: var(--color-white-d-10);
      }

      &:active {
        color: var(--color-white-d-15);
      }

      svg {
        [stroke] {
          stroke: var(--color-white);
        }

        [fill] {
          fill: var(--color-white);
        }
      }
    }

    &--danger-link {
      @extend %base-button;
      background-color: transparent;
      color: var(--color-danger);

      @include hover {
        color: var(--color-danger-d-10);
      }

      &:active {
        color: var(--color-danger-d-15);
      }

      svg {
        [stroke] {
          stroke: var(--color-danger);
        }

        [fill] {
          fill: var(--color-danger);
        }
      }
    }

    &--circle {
      @extend %base-button;
      height: auto;
      flex: 1 0 var(--spacing-5xl);
      flex-direction: column;
      justify-content: flex-start;
      padding: 0;
      font-weight: var(--font-weight-regular);
      gap: var(--spacing-xs);
      overflow-wrap: anywhere;

      @include text-sm;
    }

    &--small {
      height: 2.75rem;
    }

    &--loading:disabled { opacity: 1; }
    &__spinner { flex: none; }
    &--error { background: var(--color-error-surface); color: var(--color-error); border-color: var(--color-error); }
    &--success { background: var(--color-surface-tint); color: var(--color-accent); border-color: var(--color-accent); }
  }
</style>
