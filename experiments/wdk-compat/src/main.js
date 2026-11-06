import { Buffer } from 'buffer';
import { createApp } from 'vue';
import App from '../ui/CompatibilityApp.vue';
import '../ui/compatibility.css';
if (!globalThis.Buffer) globalThis.Buffer=Buffer;
createApp(App).mount('#app');
