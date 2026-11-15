<script>
import { Amount } from '../../lib/account/domain.js';
import MwButton from '../../components/MwButton.vue';
import MwModal from '../../components/MwModal.vue';
import MwNotice from '../../components/MwNotice.vue';
import MwListItem from '../../components/MwListItem.vue';
import MwListItems from '../../components/MwListItems.vue';
import MwFormInput from '../../components/MwForm/MwFormInput.vue';
import MwFormTextarea from '../../components/MwForm/MwFormTextarea.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormBigIntInput from '../../components/MwForm/MwFormBigIntInput.vue';
import MwFormSelect from '../../components/MwForm/MwFormSelect.vue';
import MwFormDropdown from '../../components/MwForm/MwFormDropdown.vue';
import MwFormDatepicker from '../../components/MwForm/MwFormDatepicker.vue';
import MwFormFilepicker from '../../components/MwForm/MwFormFilepicker.vue';
import MwFormTextareaReadonly from '../../components/MwForm/MwFormTextareaReadonly.vue';
import MwSwitch from '../../components/MwSwitch.vue';

export default {
  components: { MwButton, MwModal, MwNotice, MwListItem, MwListItems, MwFormInput, MwFormTextarea, MwFormAmountInput, MwFormBigIntInput, MwFormSelect, MwFormDropdown, MwFormDatepicker, MwFormFilepicker, MwFormTextareaReadonly, MwSwitch },
  data() { return { variant: 'default', longText: false, text: 'Un texto de ejemplo para revisar la interfaz', amount: Amount.fromString('9007199254740993.123456', 6), integer: 9007199254740993123456n, date: '', currency: 'USDC', file: undefined, toggled: false, modal: false, nested: false, picker: false, clicks: 0, states: ['default', 'hover', 'focus', 'active', 'disabled', 'loading', 'error', 'success'] }; },
  computed: {
    fieldState() { return { disabled: this.variant === 'disabled', isLoading: this.variant === 'loading', error: this.variant === 'error' ? 'Revisa el dato e inténtalo otra vez.' : false, success: this.variant === 'success' ? 'Dato verificado para este ejemplo.' : false }; },
  },
};
</script>
<template>
  <div class="&">
    <header><h1>Catálogo de controles · UX-03</h1><p>Componentes reales con datos de ejemplo. Esta ruta de desarrollo no crea cuentas, pagos ni órdenes.</p><RouterLink :to="{ name: 'design.prototype' }">Abrir prototipo de recorridos</RouterLink></header>
    <section><h2>Botones · ocho estados</h2><div class="&__grid"><article v-for="state in states" :key="state"><h3>{{ state }}</h3><MwButton type="primary" :data-preview-state="state" :disabled="state === 'disabled'" :isLoading="state === 'loading'" :state="['error', 'success'].includes(state) ? state : ''" @click="clicks++">{{ $t(state === 'error' ? 'Failed' : state === 'success' ? 'Completed' : 'Continue') }}</MwButton></article></div><output aria-live="polite">Activaciones: {{ clicks }}</output></section>
    <section><h2>Formularios · variantes y precisión</h2><p>Cada campo tiene una sola superficie. Revisa con Tab y con toque: un único contorno señala el campo activo; copiar y borrar mantienen su propio foco.</p><label><input v-model="longText" type="checkbox"> Textos largos</label><label class="&__state">Estado del ejemplo<select v-model="variant" aria-label="Estado de campos"><option v-for="state in ['default', 'disabled', 'loading', 'error', 'success']" :key="state">{{ state }}</option></select></label><div class="&__grid &__fields">
      <MwFormInput v-model="text" :label="longText ? 'Etiqueta larga de referencia para comprobar el reflujo y la lectura en pantallas compactas' : 'Texto editable'" placeholder="@ejemplo" v-bind="fieldState" clear required />
      <MwFormTextarea v-model="text" label="Descripción larga" v-bind="fieldState" />
      <div><MwFormAmountInput v-model="amount" label="Importe USDC exacto" :decimals="6" symbol="USDC" v-bind="fieldState" /><output data-exact-amount>{{ amount?.toString() }}</output></div>
      <div><MwFormBigIntInput v-model="integer" label="Entero exacto" v-bind="fieldState" /><output data-exact-integer>{{ integer?.toString() }}</output></div>
      <MwFormSelect v-model="currency" label="Stablecoin" :options="[{ value: 'USDC', name: 'USDC' }, { value: 'USDT', name: 'USDT' }]" v-bind="fieldState" />
      <MwFormDropdown label="Selección en diálogo" :value="currency" v-bind="fieldState" @click="!fieldState.disabled && (picker = true)" />
      <MwFormDatepicker v-model="date" label="Fecha de ejemplo" :minYear="2000" :maxYear="2030" v-bind="fieldState" />
      <MwFormFilepicker v-model="file" label="Archivo de ejemplo" fileType="document" fileAccept="text/plain" placeholder="ejemplo.txt" clear v-bind="fieldState" />
      <MwFormTextareaReadonly label="Dato público copiable" value="0x2222222222222222222222222222222222222222" copy :disabled="fieldState.disabled" />
      <MwFormInput label="Ayuda contextual" info="Información de este ejemplo" v-bind="fieldState"><template #info>La ayuda conserva el foco y permite volver al control.</template></MwFormInput>
    </div></section>
    <section><h2>Listas, selección y toque</h2><MwListItems><MwListItem title="Acción de lista" description="Enter o Espacio activa una sola vez" @click="clicks++" /><MwListItem title="Acción deshabilitada" disabled @click="clicks++" /><MwListItem title="Notificaciones de ejemplo"><template #after><MwSwitch :checked="toggled" @click="toggled = !toggled" /></template></MwListItem><MwListItem title="Selector dentro de una fila"><template #after><select v-model="currency" aria-label="Stablecoin dentro de fila"><option>USDC</option><option>USDT</option></select></template></MwListItem></MwListItems></section>
    <section><h2>Diálogo y foco</h2><div class="&__actions"><MwButton id="open-system-dialog" type="secondary" @click="modal = true">Abrir diálogo</MwButton><MwButton type="secondary" @click="modal = true; nested = true">Abrir dos niveles</MwButton></div><p>Comprobar Tab/Shift+Tab, Escape, fondo inerte, cierre exterior y retorno del foco. En Android, Atrás cierra primero el nivel superior.</p></section>
    <section><h2>Avisos, vacío y espera</h2><div class="&__grid"><MwNotice v-for="kind in ['info','loading','empty','warning','error','success']" :key="kind" :kind="kind" :title="kind" :message="kind === 'loading' ? 'Consultando. Los datos anteriores se conservan.' : kind === 'empty' ? 'Todavía no hay movimientos.' : 'Mensaje de referencia para revisar forma, contraste y jerarquía.'" :live="false"><template #action><MwButton v-if="kind === 'error'" type="secondary" small @click="clicks++">{{ $t('Refresh') }}</MwButton></template></MwNotice></div></section>
    <MwModal :show="modal" title="Diálogo de primer nivel" description="Revisa este ejemplo y vuelve al control que lo abrió." @close="nested = false; modal = false"><MwFormInput label="Campo dentro del diálogo" v-model="text" /><MwButton id="open-nested-dialog" type="secondary" @click="nested = true">Abrir segundo nivel</MwButton><template #footer><MwButton type="primary" @click="modal = false">{{ $t('Close') }}</MwButton></template></MwModal>
    <MwModal :show="nested" title="Diálogo de segundo nivel" @close="nested = false"><p>Solo este diálogo responde a Escape o Atrás.</p><MwButton type="primary" @click="nested = false">Volver al primer nivel</MwButton></MwModal>
    <MwModal :show="picker" title="Elegir stablecoin" @close="picker = false"><MwButton v-for="symbol in ['USDC','USDT']" :key="symbol" type="secondary" @click="currency = symbol; picker = false">{{ symbol }}</MwButton></MwModal>
  </div>
</template>
<style lang="scss">
/* Hallmark · component-scope · design-system: design.md · pre-emit critique: P4 H5 E4 S4 R5 V4 */
.#{ $filename } { max-width: 76rem; margin-inline: auto; color: var(--color-ink);
  header, section { margin-bottom: var(--space-xl); }
  h1 { font-size: clamp(1.5rem, 4vw, 2.5rem); letter-spacing: -0.04em; }
  h2 { font-size: 1.25rem; } h3 { font-size: 0.875rem; }
  p { max-width: 65ch; color: var(--color-muted); }
  &__grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-md); }
  &__grid > * { min-width: 0; }
  article { padding: var(--space-md); border: 1px solid var(--color-rule); border-radius: var(--radius-panel); }
  article .mw-button { width: 100%; }
  output { display: block; font-size: 0.75rem; overflow-wrap: anywhere; }
  &__state { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); margin-bottom: var(--space-lg); }
  &__state select { min-height: 2.75rem; border: 1px solid var(--color-rule); padding: var(--space-xs); }
  &__actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); }
  .mw-button[data-preview-state='hover'] { background: var(--color-accent-hover); }
  .mw-button[data-preview-state='focus'] { outline: 2px solid var(--color-focus); outline-offset: 3px; }
  .mw-button[data-preview-state='active'] { background: var(--color-accent-active); transform: translateY(1px); }
  @media (min-width: 48rem) { &__grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  @media (min-width: 75rem) { &__grid:not(&__fields) { grid-template-columns: repeat(4, minmax(0, 1fr)); } }
}
</style>
