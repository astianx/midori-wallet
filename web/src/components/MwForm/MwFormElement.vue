<script>
import { useId } from 'vue';
import MwButton from '../MwButton.vue';
import MwModal from '../MwModal.vue';
import MwLoader from '../MwLoader.vue';
import InfoIcon from '../../assets/svg/info.svg';
import TickIcon from '../../assets/svg/tick.svg';
import DangerTriangleIcon from '../../assets/svg/dangerTriangle.svg';
import { fieldProps } from './field.js';

export default {
  components: { MwButton, MwModal, MwLoader, InfoIcon, TickIcon, DangerTriangleIcon },
  props: {
    ...fieldProps,
    controlId: { type: String, default: undefined },
    errorId: { type: String, default: undefined },
    label: { type: String, default: undefined },
    error: { type: [String, Boolean], default: false },
    small: { type: Boolean, default: false },
    info: { type: [Boolean, String], default: false },
    writable: { type: Boolean, default: true },
    inheritDir: { type: Boolean, default: true },
  },
  setup() { return { localId: `field-${useId()}` }; },
  data() { return { showInfo: false }; },
  computed: {
    message() {
      if (this.error) return typeof this.error === 'string' ? this.error : this.$t('Error');
      if (this.isLoading) return this.$t('Loading');
      if (this.success) return typeof this.success === 'string' ? this.success : this.$t('Completed');
      return this.hint;
    },
  },
};
</script>
<template>
  <div class="&" :class="{ '&--small': small, '&--writable': writable, '&--has-error': error, '&--success': success && !error, '&--disabled': disabled }" :aria-busy="isLoading || undefined">
    <div class="&__container" :class="{ '&__container--with-button': $slots.button }">
      <div class="&__control" :role="$slots.boxes ? 'group' : undefined" :aria-labelledby="$slots.boxes && label ? `${localId}-label` : undefined">
        <label v-if="label" :id="`${localId}-label`" :for="controlId" class="&__label">{{ label }}<span v-if="required" aria-hidden="true"> *</span></label>
        <div class="&__wrapper">
          <div v-if="$slots.boxes" class="&__boxes" :dir="inheritDir ? undefined : 'ltr'"><slot name="boxes" /></div>
          <div v-else class="&__box" :dir="inheritDir ? undefined : 'ltr'"><slot /><span v-if="isLoading || error || success" class="&__state" aria-hidden="true"><MwLoader v-if="isLoading" /><DangerTriangleIcon v-else-if="error" /><TickIcon v-else /></span></div>
          <div v-if="$slots.extra" class="&__box &__box--extra"><slot name="extra" /></div>
        </div>
      </div>
      <MwButton v-if="info" type="base" class="&__info" :disabled="disabled" :aria-label="$t('Info')" @click="showInfo = true"><InfoIcon aria-hidden="true" /></MwButton>
      <slot name="button" />
    </div>
    <p :id="errorId || `${localId}-message`" class="&__message" :class="{ '&__error': error }" :role="error ? 'alert' : success ? 'status' : undefined">{{ message }}</p>
    <MwModal v-if="info !== false" :show="showInfo" :title="typeof info === 'string' ? info : $t('Info')" @close="showInfo = false"><slot name="info" /><template v-if="$slots.infoFooter" #footer><slot name="infoFooter" /></template></MwModal>
  </div>
</template>
<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P4 H5 E5 S5 R5 V4 */
.#{ $filename } {
  display: flex; flex-direction: column; gap: var(--space-2xs); min-width: 0;
  &__container { display: flex; align-items: flex-end; gap: var(--space-sm); min-width: 0; }
  &__control { flex: 1; min-width: 0; }
  &__label { display: block; font-size: var(--text-label); color: var(--color-muted); font-weight: 550; margin-bottom: var(--space-xs); }
  &__wrapper { display: flex; align-items: stretch; gap: var(--space-xs); min-width: 0; }
  &__box, &__boxes > div { position: relative; display: flex; align-items: center; flex: 1 1 100%; min-width: 0; min-height: 3rem; padding: var(--space-sm); border: 1px solid var(--color-rule); border-radius: var(--radius-control); background: var(--color-surface-subtle); color: var(--color-ink); gap: var(--space-xs); transition: background-color var(--dur-short) var(--ease-out), border-color var(--dur-short) var(--ease-out); }
  &__box--extra { flex: 0 0 auto; min-width: 4rem; }
  &__boxes { display: flex; width: 100%; gap: var(--space-xs); }
  &__message { min-height: 1.5em; margin: 0; font-size: 0.75rem; line-height: 1.5; color: var(--color-muted); overflow-wrap: anywhere; }
  &__state { display: flex; flex: none; align-items: center; width: 1.25rem; color: var(--color-accent); }
  &__state svg { width: 1.25rem; height: 1.25rem; }
  &__state .mw-loader { gap: var(--space-2xs); }
  &__state .mw-loader__dot { width: 0.25rem; height: 0.25rem; }
  &__info { flex: none; width: 3rem; min-height: 3rem; padding: var(--space-sm); margin-bottom: 0; }
  &__box, &__boxes > div {
    :is(input:not([type='checkbox']):not([type='radio']), textarea, select, [role='textbox'], .mw-form-dropdown__input) {
      border: 0;
      border-radius: 0;
      background: transparent;
      box-shadow: none;
      &:focus-visible { outline: none; }
    }
    &:has(:is(input:not([type='checkbox']):not([type='radio']), textarea, select, [role='textbox'], .mw-form-dropdown__input):focus) {
      border-color: var(--color-focus);
      outline: 2px solid var(--color-focus);
      outline-offset: -1px;
    }
  }
  &--has-error &__box, &--has-error &__boxes > div { border-color: var(--color-error); background: var(--color-error-surface); }
  &--has-error &__state, &__error { color: var(--color-error); }
  &--success &__box { border-color: var(--color-accent); }
  &--disabled &__wrapper { opacity: 0.55; cursor: not-allowed; }
  &--small &__box { min-height: 2.75rem; }
  @media (hover: hover) { &--writable:not(&--disabled) &__box:hover { background: var(--color-surface); } }
}
body .mw-form-element .mw-form-element__box, body .mw-form-element .mw-form-element__boxes > div { min-height: 3rem; padding: var(--space-xs) var(--space-sm); }
body .mw-form-element--small .mw-form-element__box { min-height: 2.75rem; }
.mw-form-filepicker .mw-form-filepicker__icon-after { position: absolute; inset-inline-end: var(--space-2xs); top: 50%; transform: translateY(-50%); z-index: 1; min-width: 2.75rem; min-height: 2.75rem; }
</style>
