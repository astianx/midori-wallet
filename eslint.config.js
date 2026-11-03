import vue from 'eslint-plugin-vue';
export default [
  { ignores: ['**/node_modules/**','**/dist/**','**/out/**','**/build/**','**/build-*/**','experiments/**','.local/**','assets/**','docs/**','artifacts/**','electron/resources/**'] },
  ...vue.configs['flat/base'],
  {
    files: ['**/*.js','**/*.mjs','**/*.cjs','**/*.vue'],
    languageOptions: {
      ecmaVersion: 2022, sourceType: 'module',
      globals: Object.fromEntries(['process','Buffer','console','URL','URLSearchParams','AbortSignal','AbortController','fetch','Response','TextEncoder','TextDecoder','crypto','btoa','atob','setTimeout','clearTimeout','setInterval','clearInterval','queueMicrotask','structuredClone','performance','window','document','navigator','localStorage','sessionStorage','location','screen','history','CustomEvent','DOMException','Blob','FileReader','HTMLCanvasElement','Image','Worker','WebAssembly','WebSocket','FormData','Headers','Event','requestAnimationFrame','cancelAnimationFrame','MouseEvent','indexedDB','MutationObserver','ResizeObserver','getComputedStyle','matchMedia','cordova','Fingerprint','require','__dirname','NODE_ENV'].map(name=>[name,'readonly'])),
    },
    rules: { 'no-undef':'error','no-unreachable':'error','no-dupe-args':'error','no-dupe-keys':'error','no-constant-condition':['error',{checkLoops:false}], 'vue/no-parsing-error':'error','vue/valid-template-root':'error' },
  },
];
