<script setup>
import { computed, onMounted, ref } from 'vue';
import { runOfflineCompatibility, PUBLIC_TEST_VECTOR } from '@midori/wallet-core/testing';
import { runLocalSponsorship } from '@midori/wallet-core/local-proof';
import { formatTokenAmount } from '@midori/wallet-core';
import logo from '../../../assets/brand/mark.svg?url';

const network = ref('base-sepolia');
const report = ref(null);
const running = ref(false);
const error = ref('');
const service = ref(null);
const assets = ref(null);
const copied = ref(false);
const tab = ref('wallet');
const apiOrigin = import.meta.env.VITE_API_ORIGIN || 'http://127.0.0.1:8080';
const runtime = window.midoriRuntime?.name || (window.cordova?.platformId === 'android' ? 'android-webview' : window.cordova?.platformId === 'ios' ? 'ios-wkwebview' : 'chromium-web');
const address = computed(() => report.value?.descriptor.address || PUBLIC_TEST_VECTOR.address);
const labels = {
  encryptedVaultRestored: 'Respaldo cifrado recuperado', tamperedVaultRejected: 'Respaldo alterado rechazado',
  ownerMatchesVector: 'Propietario derivado correctamente', safeMatchesVector: 'Cuenta Safe esperada',
  ownerDifferentFromSafe: 'Propietario y cuenta receptora diferenciados', signatureValid: 'Firma del propietario verificada',
  alteredMessageRejected: 'Mensaje alterado rechazado', typedSignatureValid: 'Firma EIP-712 verificada',
  restoredSameAddress: 'Misma dirección después de restaurar', restoredSameSignature: 'Firma reproducible después de restaurar',
  sharedStablecoinAddress: 'Dirección compartida para USDT y USDC', networkRequests: 'Recuperación sin backend ni RPC',
};
const status = computed(() => running.value ? 'Ejecutando' : report.value?.passed ? 'Pruebas correctas' : 'Listo para probar');
const available = computed(() => ['USDT', 'USDC'].map(symbol => {
  const asset = assets.value?.assets?.[symbol];
  const representationLabel = network.value === 'base' ? (symbol === 'USDT' ? 'USDT bridged de Ethereum' : 'USDC nativo de Circle') : (symbol === 'USDT' ? 'Sin contrato en Base Sepolia' : 'USDC de prueba');
  return { symbol, ...asset, representationLabel, amount: asset?.balanceAtomic != null ? formatTokenAmount(asset.balanceAtomic, asset.decimals) : null };
}));
async function run() {
  running.value = true;error.value = '';
  try {
    report.value = await runOfflineCompatibility({ runtime, chainId: network.value === 'base' ? 8453 : 84532 });
    window.__MIDORI_F1_REPORT__ = JSON.parse(JSON.stringify(report.value));
  } catch (failure) { error.value = failure.message; }
  finally { running.value = false; }
}
let refreshGeneration = 0;
async function refresh() {
  const generation = ++refreshGeneration;
  const selectedNetwork = network.value;
  error.value = '';
  const results = await Promise.allSettled([
    fetch(`${apiOrigin}/api/f1/status`).then(response => { if (!response.ok) throw new Error('Servidor no disponible');return response.json(); }),
    fetch(`${apiOrigin}/api/f1/assets/${selectedNetwork}?account=${encodeURIComponent(address.value)}`).then(response => { if (!response.ok) throw new Error('La consulta de red no está disponible');return response.json(); }),
  ]);
  if (generation !== refreshGeneration || selectedNetwork !== network.value) return;
  service.value = results[0].status === 'fulfilled' ? results[0].value : null;
  assets.value = results[1].status === 'fulfilled' ? results[1].value : null;
  window.__MIDORI_F1_ASSETS__ = assets.value;
  const failure = results.find(result => result.status === 'rejected');
  if (failure) error.value = failure.reason.message;
}
async function changeNetwork() { assets.value = null;await run();await refresh(); }
async function copyAddress() { try { await navigator.clipboard.writeText(address.value);copied.value = true;setTimeout(() => {copied.value=false;},1800); } catch { error.value = 'Selecciona y copia la dirección de prueba.'; } }
function downloadDescriptor() {
  if (!report.value) return;
  const url = URL.createObjectURL(new Blob([JSON.stringify(report.value.descriptor, null, 2)], { type: 'application/json' }));
  const link = document.createElement('a');link.href = url;link.download = 'midori-public-test-descriptor.json';link.click();URL.revokeObjectURL(url);
}
onMounted(async () => { await run();await refresh(); });
window.midoriF1 = { runLocalProof: config => runLocalSponsorship(config, { runtime, index: config.index }), run: () => run(), getReport: () => JSON.parse(JSON.stringify(report.value)) };
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <a class="brand" href="#"><img :src="logo" alt="" /><span>Midori<span class="brand-subtitle">Wallet</span></span></a>
      <div class="navigation-label">TU WALLET</div>
      <button :class="['nav-item', { active: tab === 'wallet' }]" @click="tab = 'wallet'"><span>◈</span> Resumen</button>
      <button :class="['nav-item', { active: tab === 'receive' }]" @click="tab = 'receive'"><span>↓</span> Recibir</button>
      <button :class="['nav-item', { active: tab === 'tests' }]" @click="tab = 'tests'"><span>✓</span> Pruebas WDK</button>
      <div class="sidebar-foot"><span class="status-dot"></span> Entorno de desarrollo<br /><small>F1 · Viabilidad de WDK</small></div>
    </aside>
    <main>
      <header><div><div class="eyebrow">DÓLARES DIGITALES</div><h1>{{ tab === 'receive' ? 'Recibir' : tab === 'tests' ? 'Viabilidad de WDK' : 'Tu cuenta, en un solo lugar.' }}</h1></div><label class="network-selector"><span class="status-dot"></span><select v-model="network" aria-label="Red" @change="changeNetwork"><option value="base-sepolia">Base Sepolia · prueba</option><option value="base">Base · solo lectura</option></select></label></header>
      <div class="test-notice"><strong>Cuenta pública de prueba.</strong> No deposites fondos. Este entorno verifica WDK; los envíos todavía no están habilitados.</div>
      <p class="receive-help">Modalidad prevista: sin necesidad de ETH. El costo de red se paga en USDT; la comisión propia de Midori es cero.</p>
      <div v-if="error" class="error-message" role="alert">{{ error }}</div>
      <section v-if="tab !== 'tests'" class="account-card">
        <div class="account-top"><span>CUENTA COMPARTIDA</span><span class="account-tag">{{ network === 'base' ? 'BASE' : 'TESTNET' }}</span></div>
        <h2>USDT + USDC</h2><p>Una dirección para ambas stablecoins en esta red.</p>
        <div class="address-row"><code data-testid="receive-address">{{ address }}</code><button @click="copyAddress">{{ copied ? 'Copiada' : 'Copiar dirección' }}</button></div>
        <div class="card-actions"><button class="button light" :disabled="running" @click="run">{{ running ? 'Comprobando…' : 'Restaurar cuenta de prueba' }}</button><button class="button outline-light" @click="downloadDescriptor">Descriptor público</button></div>
      </section>
      <div v-if="tab === 'receive'" class="receive-help"><h2>Recibir desde otra wallet</h2><p>La dirección anterior identifica esta cuenta de prueba. Los tags Midori se implementan en F4. Cambiar entre USDT y USDC mantiene la dirección; cambiar de red requiere verificar el destino.</p></div>
      <section v-if="tab === 'wallet'" class="assets-section"><div class="section-heading"><h2>Tus dólares digitales</h2><button class="text-button" @click="refresh">Actualizar lectura ↻</button></div><div class="asset-grid"><article v-for="asset in available" :key="asset.symbol" class="asset-card"><div class="asset-heading"><div :class="['token-icon', asset.symbol.toLowerCase()]">{{ asset.symbol === 'USDT' ? '₮' : '$' }}</div><div><h3>{{ asset.symbol }}</h3><span>{{ asset.representationLabel }}</span></div></div><div class="token-balance">{{ asset.amount ?? '—' }} <small>{{ asset.symbol }}</small></div><div class="token-detail">{{ asset.contract ? 'Contrato verificado en la red seleccionada' : 'No se asignó un contrato ficticio' }}</div></article></div></section>
      <section class="verification-panel"><div class="section-heading"><div><div class="eyebrow">MISMO MOTOR EN TODAS LAS PLATAFORMAS</div><h2>Crear, firmar y recuperar</h2></div><span :class="['result-badge', { passed: report?.passed }]" data-testid="proof-status">{{ status }}</span></div><div class="proof-summary"><div><span>Runtime</span><strong data-testid="runtime-name">{{ runtime }}</strong></div><div><span>Tiempo de prueba</span><strong>{{ report ? `${report.durationMs} ms` : '—' }}</strong></div><div><span>Solicitudes de red para recuperar</span><strong>{{ report?.networkRequests ?? '—' }}</strong></div></div><div v-if="report" class="check-grid"><div v-for="(passed, key) in report.checks" :key="key" class="check-item"><span :class="passed ? 'check-pass' : 'check-fail'">{{ passed ? '✓' : '×' }}</span>{{ labels[key] || key }}</div></div><div class="panel-footer"><button class="button primary" :disabled="running" @click="run">Ejecutar pruebas WDK</button><span>Firma local. Descriptor sin frase ni clave privada.</span></div></section>
      <footer><span><i :class="['status-dot', { muted: !service }]" /> {{ service ? 'Servidor local conectado' : 'Conectando con el servidor' }}</span><span>Un código · Web · Electron · Android · iOS pendiente de Mac</span></footer>
    </main>
  </div>
</template>
