import { computed, useId } from 'vue';

export const fieldProps = {
  disabled: { type: Boolean, default: false },
  isLoading: { type: Boolean, default: false },
  success: { type: [Boolean, String], default: false },
  required: { type: Boolean, default: false },
  ariaLabel: { type: String, default: undefined },
  hint: { type: String, default: '' },
};

export function useFormField(props) {
  const controlId = `control-${useId()}`;
  const errorId = `${controlId}-message`;
  const fieldAttrs = computed(() => ({
    id: controlId,
    disabled: props.disabled,
    required: props.required,
    'aria-label': props.ariaLabel || (!props.label ? props.placeholder : undefined),
    'aria-invalid': Boolean(props.error),
    'aria-disabled': props.disabled || undefined,
    'aria-busy': props.isLoading || undefined,
    'aria-describedby': props.error || props.success || props.hint || props.isLoading ? errorId : undefined,
  }));
  return { controlId, errorId, fieldAttrs };
}
