import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';
export default defineConfig({
  base: './',
  plugins:[vue(),{name:'cordova-proof-entry',transformIndexHtml(html){return process.env.WDK_RUNTIME_TARGET==='phonegap'?html.replace('</head>','<script src="cordova.js"></script></head>'):html;}}],
  server:{host:'127.0.0.1',port:4173,strictPort:true,open:false,fs:{allow:[path.resolve('../..')]}},
  build:{target:'es2022',outDir:'dist',emptyOutDir:true},
});
