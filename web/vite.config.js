import { uiRuntimeAliases } from './ui-runtime.config.js';
import { nodePolyfills } from 'vite-plugin-node-polyfills';
/* eslint-env node */
import { ViteEjsPlugin } from 'vite-plugin-ejs';
import { ViteMinifyPlugin } from 'vite-plugin-minify';
import basicSsl from '@vitejs/plugin-basic-ssl';
import path from 'path';
import { sentryVitePlugin } from '@sentry/vite-plugin';
import svgLoader from 'vite-svg-loader';
import vue from '@vitejs/plugin-vue';
import { defineConfig, loadEnv } from 'vite';



export default defineConfig(({ mode }) => {
  const isHttps = process.argv.includes('--https');
  const env = loadEnv(mode, './');
  const development = { ...loadEnv(mode, './', 'MIDORI_'), ...process.env };
  const apiOrigin = development.MIDORI_DEV_API_ORIGIN || env.VITE_API_ORIGIN;
  if (apiOrigin && !['http:', 'https:'].includes(new URL(apiOrigin).protocol)) throw new Error('Invalid API origin');
  return {
    cacheDir: process.env.MIDORI_VITE_CACHE_DIR || 'node_modules/.vite',
    resolve: { alias: [{ find: '@midori/wallet-core', replacement: path.resolve('../packages/wallet-core/src/index.js') }, ...uiRuntimeAliases, ...['buffer', 'global', 'process'].map(name => ({ find: `vite-plugin-node-polyfills/shims/${name}`, replacement: path.resolve(`node_modules/vite-plugin-node-polyfills/shims/${name}/dist/index.js`) }))] },
    server: {
      host: development.MIDORI_DEV_HOST || '127.0.0.1',
      port: Number(development.MIDORI_DEV_PORT || 5173),
      fs: { allow: [path.resolve('..')] },
      open: false,
      strictPort: true,
      proxy: {
        ...(apiOrigin ? { '/api': { target: apiOrigin, headers: isHttps ? { 'X-Forwarded-Proto': 'https' } : {} } } : {}),
      },
    },
    base: process.env.BASE_URL || '/',
    plugins: [
      nodePolyfills(),

      vue({
        template: {
          compilerOptions: {
            nodeTransforms: [bem],
          },
        },
      }),
      svgLoader({ svgo: false }),
      sfcAutoName(),
      ViteEjsPlugin({ env }),
      ViteMinifyPlugin(),
      sentryVitePlugin({
        org: process.env.SENTRY_ORG,
        project: process.env.SENTRY_PROJECT,
        authToken: process.env.SENTRY_AUTH_TOKEN,
        telemetry: false,
        sourcemaps: {
          filesToDeleteAfterUpload: outDir(env.VITE_BUILD_TYPE) + '/**/*.map',
        },
        disable: true,
      }),
      ...(isHttps ? [basicSsl()] : []),
    ],
    build: {
      target: 'es2022',
      minify: true,
      outDir: outDir(env.VITE_BUILD_TYPE),
      emptyOutDir: true,
      sourcemap: 'hidden',
      rollupOptions: {
        input: inputs(env.VITE_BUILD_TYPE),
        preserveEntrySignatures: env.VITE_BUILD_TYPE === 'electron' ? 'exports-only' : undefined,
        output: {
          entryFileNames(info) {
            if (info.name === 'variables') {
              return 'variables.js';
            }
            if (info.name === 'schemes') {
              return 'schemes.js';
            }
            return 'assets/js/[name]-[hash].js';
          },
          chunkFileNames(info) {
            const defaultName = 'assets/js/[name]-[hash].js';
            if (/src\/lib\/i18n\/messages\/[a-z-]+\.json/.test(info.facadeModuleId)) {
              const match = /messages\/([a-z-]+).json/.exec(info.facadeModuleId);
              return `assets/js/i18n/${match[1]}-[hash].js`;
            }
            if (info.name === 'index') {
              const id = info.facadeModuleId || info.moduleIds[0];
              const match = /node_modules\/((@[^/]*\/)?[^/]*)\//.exec(id);
              if (!match) return defaultName;
              return `assets/js/${match[1]}-[hash].js`;
            }
            return defaultName;
          },
          assetFileNames({ name }) {
            let dir = '';
            if (/\.css$/.test(name)) dir = 'css/';
            if (/\.wasm$/.test(name)) dir = 'wasm/';
            if (/\.svg$/.test(name)) dir = 'img/';
            return `assets/${dir}[name]-[hash][extname]`;
          },
        },
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: (content, filename) => {
            const kebab = kebabanize(filename);
            const str = `
              @import "${path.resolve('src/assets/styles/mixins')}";
              $filename: "${kebab}";
            `;
            return str + content;
          },
        },
      },
    },
  };
});

function kebabanize(componentPath) {
  return path.basename(componentPath, '.vue').replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
}

function bem(node, context) {
  if (!node.props) return;
  const kebab = kebabanize(context.selfName);
  node.props.forEach((prop) => {
    if (prop.name === 'class') {
      prop.value.content = prop.value.content.replace(/&/g, kebab);
    }
    if (prop.name === 'bind' && prop.arg?.content === 'class') {
      if (prop.exp.children) {
        prop.exp.children = prop.exp.children.map((child) => {
          if (typeof child === 'string') {
            child = child.replace(/&([-_])/g, `${kebab}$1`);
          }
          return child;
        });
      }
      if (prop.exp.content) {
        prop.exp.content = prop.exp.content.replace(/&/g, kebab);
      }
    }
  });
}

function sfcAutoName() {
  return {
    name: 'sfc-auto-name',
    enforce: 'pre',
    transform(code, id) {
      if (!/\.vue$/.test(id)) return;
      const name = path.basename(id, '.vue');
      return {
        code: code.replace(/(export default \{)/, `$1 name: '${name}',`),
        map: null,
      };
    },
  };
}

function outDir(buildType) {
  switch (buildType) {
    case 'electron':
      return '../electron/dist';
    case 'phonegap':
      return '../phonegap/build/www';
    default:
      return '../server/dist';
  }
}

function inputs(buildType) {
  const inputs = {
    main: './index.html',
  };
  if (buildType === 'web') {
    // Authentication pages are introduced with the Midori API in F2.
  }
  if (buildType === 'electron') {
    inputs['variables'] = './src/variables.js';
    inputs['schemes'] = './src/lib/schemes.js';
  }
  return inputs;
}
