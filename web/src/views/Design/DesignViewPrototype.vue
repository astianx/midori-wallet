<script>
import model from './fintech-model.js';
import QRCode from 'qrcode-svg';
import { Amount } from '../../lib/account/domain.js';
import MwButton from '../../components/MwButton.vue';
import MwModal from '../../components/MwModal.vue';
import MwNotice from '../../components/MwNotice.vue';
import MwFlowProgress from '../../components/MwFlowProgress.vue';
import MwFormInput from '../../components/MwForm/MwFormInput.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormTextareaReadonly from '../../components/MwForm/MwFormTextareaReadonly.vue';

export default {
  components: { MwButton, MwModal, MwNotice, MwFlowProgress, MwFormInput, MwFormAmountInput, MwFormTextareaReadonly },
  data() { return { model, context: 'desktop', screen: 'access', condition: 'fresh', symbol: 'USDC', recipient: '$ui-destino', amount: Amount.fromString('12.345678', 6), message: '', handoff: '', rampEnabled: true, keyboard: false, hidden: false, authEntry: 'create', origin: 'external', backedUp: false, understandsBackup: false, checkOne: '', checkTwo: '', fiat: Amount.fromString('20', 2), fiatKind: 'buy', fiatCurrency: 'USD', events: [] }; },
  computed: {
    qr() { return new QRCode({ content: this.origin === 'midori' ? '$ui-destino' : '0x2222222222222222222222222222222222222222', join: true, padding: 0, container: 'svg-viewbox' }).svg(); },
    authFlow() { return ['access','onboarding-backup','onboarding-verify','onboarding-pin','recover-entry','unlock'].includes(this.screen); },
    page() { return model.screens.find(item => item.id === this.screen); },
    status() { return model.cases.find(item => item.id === this.condition); },
    flow() { return this.screen.startsWith('send-'); },
    total() { return this.amount ? new Amount(this.amount.value + 5000n, 6).toString() : '—'; },
    unavailable() { return ['unavailable', 'loading'].includes(this.condition); },
    statusKind() { return ['invalid', 'temporary-error', 'reverted'].includes(this.condition) ? 'error' : this.condition === 'confirmed' ? 'success' : this.condition === 'loading' ? 'loading' : ['zero','fresh','refreshing','pending'].includes(this.condition) ? 'info' : ['empty', 'filtered'].includes(this.condition) ? 'empty' : 'warning'; },
  },
  watch: { fiatKind() { this.fiat = undefined; this.message = ''; }, recipient() { this.message = ''; }, amount() { this.message = ''; }, screen(value) { if (['ramps','receive','send-recipient'].includes(value) && !['USDC','USDT'].includes(this.symbol)) this.symbol = 'USDC'; if (['send-pending','receipt'].includes(value) && !['pending','ambiguous','confirmed','reverted'].includes(this.condition)) this.condition = 'pending'; } },
  methods: {
    createExample() { this.authEntry = 'create'; this.backedUp = false; this.understandsBackup = false; this.checkOne = ''; this.checkTwo = ''; this.go('onboarding-backup'); },
    go(screen) { this.events.push({ from: this.screen, to: screen, condition: this.condition }); this.screen = screen; this.message = ''; },
    advance() {
      if (this.screen === 'onboarding-verify' && (this.checkOne !== 'ejemplo' || this.checkTwo !== 'ejemplo')) { this.message = 'Completa las dos comprobaciones de ejemplo.'; return; }
      if (this.screen === 'send-recipient' && !this.recipient.trim()) { this.message = 'Escribe un tag o una dirección para continuar.'; return; }
      if (this.screen === 'ramps' && (!this.fiat || this.fiat.value <= 0n)) { this.message = 'Escribe un importe mayor que cero.'; return; }
      if (this.screen === 'send-amount' && this.condition === 'zero') { this.message = 'Tu saldo no alcanza para este pago.'; return; }
      if (this.screen === 'send-amount' && (!this.amount || this.amount.value <= 0n)) { this.message = 'Escribe un importe mayor que cero.'; return; }
      if (['send-review','send-pin'].includes(this.screen) && this.condition === 'expired') return;
      if (this.screen === 'ramps' && !this.rampEnabled) return;
      if (this.screen === 'onboarding-pin' && this.authEntry === 'create') this.condition = 'zero';
      if (this.screen === 'send-pin') this.condition = 'pending';
      if (this.screen === 'ramp-provider') this.condition = 'ramp-pending';
      this.go(this.page.next);
    },
    back() { const previous = { 'onboarding-backup': 'access', 'onboarding-verify': 'onboarding-backup', 'onboarding-pin': this.authEntry === 'recover' ? 'recover-entry' : 'onboarding-verify', 'recover-entry': 'access', unlock: 'access', 'send-recipient': 'home', 'send-amount': 'send-recipient', 'send-review': 'send-amount', 'send-pin': 'send-review', 'send-pending': 'home', receipt: 'activity', 'ramp-provider': 'ramps', 'ramp-order': 'ramps', recovery: 'account' }; this.go(previous[this.screen] || 'home'); },
  },
};
</script>
<template>
  <div class="&">
    <header class="&__heading"><h1>Prototipo navegable · {{ model.version }}</h1><p>Datos de ejemplo. Sin pagos, firmas, KYC ni órdenes reales. Los controles permiten revisar decisiones de UX-02.</p><RouterLink :to="{ name: 'design.system' }">Abrir catálogo de componentes</RouterLink></header>
    <div class="&__toolbar"><label>Contexto<select v-model="context" aria-label="Contexto del prototipo"><option value="desktop">Web escritorio</option><option value="compact">Web compacta</option><option value="native">App móvil</option></select></label><label>Pantalla<select v-model="screen" aria-label="Pantalla del prototipo"><option v-for="item in model.screens" :key="item.id" :value="item.id">{{ item.title }}</option></select></label><label>Estado<select v-model="condition" aria-label="Estado del prototipo"><option v-for="item in model.cases" :key="item.id" :value="item.id">{{ item.title }}</option></select></label><label><input v-model="rampEnabled" type="checkbox"> Capacidad rampa (ejemplo)</label><label><input v-model="keyboard" type="checkbox"> Reservar espacio de teclado</label></div>
    <div class="&__stage" :data-context="context" :data-screen="screen" :class="{ '&__stage--auth': authFlow }">
      <aside v-if="context === 'desktop' && !authFlow" class="&__rail"><strong>Midori Wallet</strong><button v-for="item in ['home','activity','receive','account']" :key="item" :aria-current="screen === item ? 'page' : undefined" @click="go(item)">{{ model.screens.find(s => s.id === item).title }}</button></aside>
      <aside v-if="context === 'desktop' && authFlow" class="&__auth-summary"><strong>Midori Wallet</strong><h2>Stablecoins, en tus manos.</h2><p>Recibe y envía USDC y USDT con tu propia wallet.</p><span>USDC · USDT · Base</span></aside>
      <main class="&__main"><div v-if="context === 'native'" class="&__safe-top">Área segura de app</div><header class="&__app-heading"><button v-if="screen !== 'access' && screen !== 'home'" type="button" @click="back">← Atrás</button><h2>{{ page.title }}</h2><span>USDC · USDT · Base</span></header>
        <div class="&__content">
          <template v-if="screen === 'access'"><h2>Stablecoins, en tus manos.</h2><p>Envía y recibe USDC y USDT con tu propia wallet.</p><MwButton type="primary" data-proto-action="create" @click="createExample">{{ $t('Create new wallet') }}</MwButton><MwButton type="secondary" @click="authEntry = 'recover'; go('recover-entry')">{{ $t('Open existing wallet') }}</MwButton><p>La creación y recuperación mantienen sus pasos. Este prototipo no genera ni recibe una frase o PIN reales.</p><MwButton type="primary-link" @click="go('unlock')">Desbloquear sesión de ejemplo</MwButton></template>
          <template v-else-if="screen === 'onboarding-backup'"><h3>Guarda tus 12 palabras</h3><p>En producto se muestran las palabras de recuperación. Aquí se representan sin generar secretos.</p><div class="&__backup"><span v-for="number in 12" :key="number">{{ number }} · ejemplo</span></div><label><input v-model="backedUp" type="checkbox"> He guardado el respaldo de ejemplo</label><label><input v-model="understandsBackup" type="checkbox"> Entiendo que las palabras permiten recuperar la wallet</label></template>
          <template v-else-if="screen === 'onboarding-verify'"><p>Usa la palabra «ejemplo» en ambos campos. Nunca introduzcas palabras de una wallet real en el prototipo.</p><MwFormInput v-model="checkOne" label="Comprobación 1 de ejemplo" :error="message" /><MwFormInput v-model="checkTwo" label="Comprobación 2 de ejemplo" /></template>
          <template v-else-if="['onboarding-pin','unlock'].includes(screen)"><h3>PIN de acceso</h3><p aria-label="PIN representado con cuatro posiciones vacías">○ ○ ○ ○</p><p>Este paso representa la protección del acceso. No se solicita ningún PIN real.</p></template>
          <template v-else-if="screen === 'recover-entry'"><h3>Recupera con tus 12 palabras</h3><p>El archivo opcional contiene el descriptor y no sustituye las palabras.</p><MwFormTextareaReadonly label="Frase de recuperación" value="12 posiciones de ejemplo; ningún secreto real" /><MwFormTextareaReadonly label="Archivo opcional" value="descriptor de ejemplo" /></template>
          <template v-else-if="screen === 'home'"><div class="&__overview"><section class="&__balance"><div class="&__row"><h3>{{ $t('Balance') }}</h3><button type="button" :aria-label="$t(hidden ? 'Show balance' : 'Hide balance')" @click="hidden = !hidden">{{ hidden ? 'Mostrar' : 'Ocultar' }}</button></div><div class="&__number">{{ hidden ? '••••' : unavailable ? '—' : condition === 'zero' ? '≈ 0,00' : '≈ 375,12' }} <small>USD</small></div><p>{{ $t('Reference: USD 1 per token. Live prices unavailable.') }}</p><div class="&__actions"><MwButton type="primary" data-proto-action="send" @click="go('send-recipient')">{{ $t('Send') }}</MwButton><MwButton type="secondary" data-proto-action="receive" @click="go('receive')">{{ $t('Receive') }}</MwButton></div></section><section class="&__panel"><h3>Tu tag Midori</h3><p>Comparte tu tag para recibir de otra persona en Midori.</p><MwButton type="secondary" @click="go('receive')">$ui-destino</MwButton></section></div><MwNotice v-if="['loading','unavailable','stale','refreshing','zero'].includes(condition)" :kind="statusKind" :title="status.title" :message="status.copy" /><div class="&__assets"><button v-for="asset in ['USDC','USDT']" :key="asset" @click="symbol = asset; go('send-recipient')"><strong>{{ asset }}</strong><span>{{ hidden ? '••••' : unavailable ? '—' : condition === 'zero' ? '0' : asset === 'USDC' ? '250.1256' : '125' }} {{ asset }} · Base</span></button></div><div v-if="rampEnabled" class="&__actions"><MwButton type="secondary" @click="fiatKind = 'buy'; go('ramps')">{{ $t('Add money') }}</MwButton><MwButton type="secondary" @click="fiatKind = 'sell'; go('ramps')">{{ $t('Withdraw') }}</MwButton></div><MwButton type="primary-link" @click="go('activity')">{{ $t('Activity') }}</MwButton></template>
          <template v-else-if="['send-recipient','send-amount','send-review'].includes(screen)"><MwFlowProgress :current="['send-recipient','send-amount','send-review'].indexOf(screen)" /><div class="&__form-layout"><div class="&__form">
            <template v-if="screen === 'send-recipient'"><label>Stablecoin<select v-model="symbol" aria-label="Stablecoin para enviar"><option>USDC</option><option>USDT</option></select></label><MwFormInput v-model="recipient" :label="$t('Wallet address or Midori tag')" placeholder="$ui-destino" :error="message || (condition === 'invalid' ? status.copy : false)" clear /><p>Tag de ejemplo resuelto: $ui-destino · Base.</p></template>
            <template v-else-if="screen === 'send-amount'"><MwFormTextareaReadonly :label="$t('To')" :value="recipient" /><MwFormAmountInput v-model="amount" :label="$t('Amount')" :symbol="symbol" :decimals="6" :error="message" /><p>El costo de red se revisará en {{ symbol }}, el mismo token del envío.</p></template>
            <template v-else><MwFormTextareaReadonly :label="$t('Recipient receives')" :value="`${amount?.toString() || '—'} ${symbol}`" /><MwFormTextareaReadonly :label="$t('To')" :value="recipient" /><details><summary>{{ $t('View wallet address') }}</summary><p>0x2222222222222222222222222222222222222222 · Base</p></details><dl><dt>{{ $t('Midori fee') }}</dt><dd>0 {{ symbol }}</dd><dt>{{ $t('Estimated network cost') }}</dt><dd>0.0025 {{ symbol }} · ejemplo</dd><dt>{{ $t('Maximum total debit') }}</dt><dd>{{ total }} {{ symbol }}</dd></dl><MwNotice v-if="condition === 'expired'" kind="warning" :message="status.copy" /><MwButton type="primary-link" data-proto-action="edit" @click="condition = 'fresh'; go('send-amount')">{{ condition === 'expired' ? $t('Review again') : $t('Edit payment') }}</MwButton></template>
          </div><aside class="&__context"><h3>Contexto del envío</h3><p>{{ symbol }} · Base</p><p>Destinatario y monto se conservan al editar. La autorización sigue después de revisar.</p></aside></div></template>
          <template v-else-if="screen === 'send-pin'"><MwNotice v-if="condition === 'expired'" kind="warning" :message="status.copy" /><MwButton v-if="condition === 'expired'" type="primary-link" @click="condition = 'fresh'; go('send-review')">{{ $t('Review again') }}</MwButton><h3>{{ $t('Enter your PIN') }}</h3><p aria-label="PIN simulado, sin ingresar datos reales">••••</p><p>Autorización representada con datos de ejemplo. No introduzcas un PIN real.</p></template>
          <template v-else-if="screen === 'send-pending'"><MwNotice :kind="statusKind" :title="status.title" :message="status.copy" /><p>{{ amount?.toString() }} {{ symbol }} · {{ recipient }}</p><p>Referencia: UI-ONLY-PAYMENT</p></template>
          <template v-else-if="screen === 'receipt'"><MwNotice :kind="statusKind" :title="status.title" :message="status.copy" /><dl><dt>{{ $t('Amount') }}</dt><dd>{{ amount?.toString() }} {{ symbol }}</dd><dt>{{ $t('To') }}</dt><dd>{{ recipient }}</dd><dt>{{ $t('Network') }}</dt><dd>Base</dd><dt>{{ $t('Date') }}</dt><dd>15 noviembre 2026 · dato de ejemplo</dd><dt>{{ $t('Transaction ID') }}</dt><dd>UI-ONLY-PAYMENT</dd><dt>{{ $t('Network cost') }}</dt><dd>No disponible en este ejemplo. No se presenta como cero.</dd></dl></template>
          <template v-else-if="screen === 'receive'"><div class="&__actions"><MwButton :type="origin === 'midori' ? 'primary' : 'secondary'" @click="origin = 'midori'">Midori Wallet</MwButton><MwButton :type="origin === 'external' ? 'primary' : 'secondary'" @click="origin = 'external'">{{ $t('Other services') }}</MwButton></div><label>Stablecoin<select v-model="symbol" aria-label="Stablecoin para recibir"><option>USDC</option><option>USDT</option></select></label><p>{{ origin === 'external' ? `Envía solo ${symbol} por la red Base.` : 'Comparte tu tag con otra persona que use Midori.' }}</p><div class="&__qr"><!-- eslint-disable-next-line vue/no-v-html -->
              <div v-html="qr" /></div><MwFormTextareaReadonly :label="origin === 'midori' ? $t('Your Midori tag') : $t('Your wallet address')" :value="origin === 'midori' ? '$ui-destino' : '0x2222222222222222222222222222222222222222'" copy /><MwButton type="secondary" @click="handoff = 'share'">{{ $t('Share') }}</MwButton><p>USDC y USDT en Base comparten dirección. El contrato del token es un dato distinto.</p></template>
          <template v-else-if="screen === 'ramps'"><MwNotice kind="warning" title="Transak · STAGING" message="Ejemplo de modo de prueba; no representa una operación real." /><MwNotice v-if="!rampEnabled || condition === 'unsupported'" kind="warning" :message="model.cases.find(s => s.id === 'unsupported').copy" /><template v-else><label>Operación<select v-model="fiatKind" aria-label="Tipo de rampa"><option value="buy">Agregar dinero</option><option value="sell">Retirar</option></select></label><label>Stablecoin<select v-model="symbol" aria-label="Stablecoin de rampa"><option>USDC</option><option>USDT</option></select></label><MwFormAmountInput v-model="fiat" :decimals="fiatKind === 'buy' ? 2 : 6" :symbol="fiatKind === 'buy' ? 'USD' : symbol" :error="message" label="Monto de ejemplo" /><p>Tasa, comisión, métodos y recepción final se confirmarán dentro del proveedor. No hay cotización disponible en este prototipo.</p></template></template>
          <template v-else-if="screen === 'ramp-provider'"><h3>Continuar con Transak</h3><p>El proveedor confirma métodos, costo final y verificación cuando corresponda.</p><MwNotice kind="info" message="Representación del paso externo; no se abre un widget ni se envían datos." /><p>Volver no equivale a cancelar ni confirmar una orden.</p></template>
          <template v-else-if="screen === 'ramp-order'"><MwNotice kind="warning" title="Transak · STAGING" :message="model.cases.find(s => s.id === 'ramp-pending').copy" /><dl><dt>{{ $t('Status') }}</dt><dd>{{ $t('Processing') }}</dd><dt>Referencia</dt><dd>UI-ONLY-ORDER</dd><dt>{{ $t('Amount') }}</dt><dd>{{ fiat?.toString() }} {{ fiatKind === 'buy' ? 'USD' : symbol }} · entrada de ejemplo</dd><dt>Recepción final</dt><dd>Por confirmar en proveedor.</dd></dl></template>
          <template v-else-if="screen === 'activity'"><div class="&__actions"><MwButton v-for="asset in ['Todos','USDC','USDT']" :key="asset" type="secondary" small @click="symbol = asset; condition = asset === 'USDT' ? 'filtered' : condition === 'filtered' ? 'pending' : condition">{{ asset }}</MwButton></div><MwNotice v-if="['zero','empty','filtered','temporary-error'].includes(condition)" :kind="['zero','empty'].includes(condition) ? 'empty' : statusKind" :message="condition === 'zero' ? model.cases.find(c => c.id === 'empty').copy : status.copy"><template #action><MwButton type="secondary" small @click="['zero','empty'].includes(condition) ? go('receive') : (condition = 'pending', symbol = 'Todos')">{{ ['zero','empty'].includes(condition) ? $t('Receive') : condition === 'filtered' ? 'Mostrar todos' : $t('Refresh') }}</MwButton></template></MwNotice><button v-for="(item, index) in ['zero','empty','filtered'].includes(condition) ? [] : ['Transferencia · USDC','Orden Transak · STAGING']" :key="item" class="&__activity-row" @click="index === 0 ? go('receipt') : go('ramp-order')"><span>{{ item }}</span><span>{{ index === 0 ? '12.345678 USDC' : '20 USD' }}</span><small>{{ index === 0 ? status.title : 'En proceso · modo de prueba' }}</small></button></template>
          <template v-else-if="screen === 'account'"><div class="&__account-grid"><section v-for="group in ['Perfil y tag','Preferencias','Seguridad y recuperación','Conexiones','Ayuda','Acerca de']" :key="group" class="&__panel"><h3>{{ group }}</h3><MwButton type="primary-link" @click="group.includes('recuperación') ? go('recovery') : (message = `${group}: acceso conservado en producto.`)">{{ group.includes('recuperación') ? $t('Recovery file') : 'Abrir' }}</MwButton></section></div><p role="status">{{ message }}</p></template>
          <template v-else-if="screen === 'recovery'"><h3>12 palabras y archivo opcional</h3><p>Las 12 palabras restauran tu wallet. El archivo opcional contiene el descriptor de cuenta y no incluye claves privadas.</p><MwButton type="secondary" @click="handoff = 'save'">Guardar archivo de ejemplo</MwButton><MwButton type="secondary" @click="message = 'Archivo de ejemplo verificado; no se ha leído ningún archivo real.'">{{ $t('Verify saved recovery file') }}</MwButton><p role="status">{{ message }}</p></template>
        </div>
        <footer v-if="['onboarding-backup','onboarding-verify','onboarding-pin','recover-entry','unlock','send-recipient','send-amount','send-review','send-pin','send-pending','receipt','ramps','ramp-provider','ramp-order','recovery'].includes(screen)" class="&__footer"><MwButton type="primary" data-proto-action="primary" :disabled="(screen === 'onboarding-backup' && (!backedUp || !understandsBackup)) || (['send-review','send-pin'].includes(screen) && condition === 'expired') || (screen === 'ramps' && (!rampEnabled || condition === 'unsupported'))" @click="advance">{{ page.primary }}</MwButton></footer>
        <nav v-if="context !== 'desktop' && !authFlow && !flow" class="&__bottom" aria-label="Destinos del prototipo"><button v-for="item in ['home','activity','receive','account']" :key="item" @click="go(item)">{{ model.screens.find(s => s.id === item).title }}</button></nav><div v-if="context === 'native'" class="&__safe-bottom">Área segura inferior</div><div v-if="keyboard && context !== 'desktop'" class="&__keyboard">Espacio del teclado del sistema · 280 px</div>
      </main>
    </div>
    <MwModal :show="Boolean(handoff)" :title="handoff === 'share' ? 'Compartir dato de recepción' : 'Guardar descriptor de ejemplo'" @close="handoff = ''"><p>{{ handoff === 'share' ? 'En la aplicación, este paso abre las opciones de compartir del sistema cuando están disponibles, o permite copiar el dato. Aquí se representa la entrega sin enviar mensajes.' : 'En la aplicación, se guarda el descriptor opcional mediante el selector de archivos. Aquí se representa ese paso sin descargar ni crear un respaldo real.' }}</p><MwButton type="primary" @click="handoff = ''">Volver</MwButton></MwModal>
    <aside class="&__spec"><h2>Especificación de esta pantalla</h2><p>Orden de lectura: {{ page.blocks.join(' → ') }}. Acción principal: {{ page.primary }}. Retorno: conserva el borrador y el contexto.</p><p>Estado: {{ status.rule }}</p><output aria-label="Registro de navegación">{{ events.length }} transiciones locales. Ninguna operación financiera.</output></aside>
  </div>
</template>
<style lang="scss">
/* Hallmark · genre: modern-minimal · macrostructure: Workbench · design-system: design.md · pre-emit critique: P4 H5 E4 S5 R5 V4 */
.#{ $filename } {
  color: var(--color-ink); max-width: 86rem; margin-inline: auto;
  &__heading p { max-width: 65ch; color: var(--color-muted); }
  h1 { font-size: clamp(1.5rem, 4vw, 2.5rem); letter-spacing: -0.04em; }
  &__toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-md); padding-block: var(--space-lg); }
  &__toolbar label { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); font-size: 0.8125rem; }
  select { display: block; min-height: 2.75rem; padding: var(--space-xs); border: 1px solid var(--color-rule); border-radius: var(--radius-control); max-width: 100%; }
  &__stage { display: grid; grid-template-columns: minmax(0, 1fr); border: 1px solid var(--color-rule); background: var(--color-paper); }
  &__stage[data-context='desktop'] { grid-template-columns: 11rem minmax(0, 1fr); }
  &__stage[data-context='desktop'].design-view-prototype__stage--auth { grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr); }
  &__auth-summary { display: flex; flex-direction: column; gap: var(--space-lg); padding: var(--space-lg); background: var(--color-brand-panel); color: var(--color-brand-panel-ink); }
  &__stage:not([data-context='desktop']) .design-view-prototype__overview > .design-view-prototype__panel { display: none; }
  &__stage:not([data-context='desktop']) { max-width: 26rem; margin-inline: auto; }
  &__rail { display: flex; flex-direction: column; gap: var(--space-sm); border-inline-end: 1px solid var(--color-rule); padding: var(--space-md); background: var(--color-surface); }
  &__rail button, &__bottom button, &__app-heading > button { min-height: 2.75rem; text-align: start; padding: var(--space-xs); border-radius: var(--radius-control); white-space: nowrap; font-size: 0.8125rem; }
  &__rail button:hover { background: var(--color-surface-tint); }
  &__main { display: flex; flex-direction: column; min-width: 0; }
  &__app-heading { display: flex; flex-wrap: wrap; gap: var(--space-sm); align-items: center; padding: var(--space-md); border-bottom: 1px solid var(--color-rule); }
  &__app-heading h2 { font-size: 1.125rem; margin: 0; flex: 1; }
  &__app-heading span { font-size: 0.6875rem; color: var(--color-muted); }
  &__content { display: flex; flex-direction: column; gap: var(--space-md); min-width: 0; padding: var(--space-md); min-height: 25rem; }
  &__stage:not([data-context='desktop']) &__content { max-height: 34rem; overflow-y: auto; }
  &__overview, &__assets, &__account-grid, &__form-layout { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-md); }
  &__balance { padding: var(--space-lg); border-radius: var(--radius-panel); background: var(--color-surface-tint); }
  &__number { font-size: 2.25rem; font-variant-numeric: tabular-nums; font-weight: 600; overflow-wrap: anywhere; }
  &__number small { font-size: 0.875rem; }
  &__row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-xs); }
  &__row button { min-height: 2.75rem; font-size: 0.75rem; }
  &__panel, &__context { padding: var(--space-md); border: 1px solid var(--color-rule); border-radius: var(--radius-panel); background: var(--color-surface); }
  &__form { min-width: 0; display: flex; flex-direction: column; gap: var(--space-sm); }
  &__context { display: none; }
  &__actions { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
  &__actions > .mw-button { flex: 1; }
  &__assets button { display: flex; min-height: 4.5rem; align-items: center; justify-content: space-between; gap: var(--space-xs); padding: var(--space-md); border: 1px solid var(--color-rule); border-radius: var(--radius-panel); background: var(--color-surface); }
  &__assets span { font-size: 0.75rem; }
  &__backup { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-xs); font-size: 0.75rem; }
  &__backup span { padding: var(--space-xs); border: 1px solid var(--color-rule); border-radius: var(--radius-control); }
  &__qr > div { width: 9rem; background: var(--color-white); padding: var(--space-xs); }
  &__qr { display: grid; place-items: center; min-height: 10rem; border: 1px dashed var(--color-rule); color: var(--color-muted); font-size: 0.8125rem; }
  &__activity-row { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-xs); min-height: 4rem; text-align: start; padding: var(--space-md); border-bottom: 1px solid var(--color-rule); }
  &__activity-row small { color: var(--color-muted); }
  &__footer { padding: var(--space-md); border-top: 1px solid var(--color-rule); background: var(--color-surface); }
  &__footer .mw-button { width: 100%; }
  &__bottom { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border-top: 1px solid var(--color-rule); background: var(--color-surface); }
  &__bottom button { font-size: 0.625rem; text-align: center; padding: var(--space-xs) 0; }
  &__safe-top, &__safe-bottom { min-height: 1.5rem; display: grid; place-items: center; background: var(--color-surface-subtle); color: var(--color-muted); font-size: 0.625rem; }
  &__safe-bottom { min-height: 2rem; }
  &__keyboard { height: 17.5rem; flex: none; display: grid; place-items: center; background: var(--color-surface-subtle); color: var(--color-muted); padding: var(--space-md); font-size: 0.8125rem; }
  &__spec { margin-top: var(--space-lg); font-size: 0.875rem; max-width: 70ch; }
  p, dd { overflow-wrap: anywhere; } dl { margin: 0; } dt { color: var(--color-muted); font-size: 0.8125rem; } dd { margin: var(--space-xs) 0 var(--space-md); font-variant-numeric: tabular-nums; }
  @media (min-width: 64rem) { &__stage[data-context='desktop'] { .design-view-prototype__overview, .design-view-prototype__assets, .design-view-prototype__account-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .design-view-prototype__form-layout { grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); } .design-view-prototype__context { display: block; } } }
  @media (max-width: 40rem) { &__stage[data-context='desktop'].design-view-prototype__stage--auth { grid-template-columns: minmax(0, 1fr); } &__stage[data-context='desktop'] { grid-template-columns: minmax(0, 1fr); } &__rail { flex-direction: row; flex-wrap: wrap; border: 0; } &__rail strong { width: 100%; } }
}
</style>
