# Midori Wallet

Cliente Vue compartido por web, Electron, Android e iOS, con WDK, PostgreSQL, USDT y USDC en Base. Firma local, recuperación portable y costo de red en USDT; la comisión propia Midori es cero.

Node 22.22.2 y npm 10–12. Instalar desde los locks públicos:

```sh
npm ci --ignore-scripts
npm --prefix packages/wallet-core ci --ignore-scripts
npm --prefix server ci --ignore-scripts
npm --prefix web ci --ignore-scripts
npm --prefix electron ci --ignore-scripts
node electron/node_modules/electron/install.js
npm --prefix phonegap ci --ignore-scripts
```

Preparar los `.env` a partir de los ejemplos de cada proyecto, sin versionarlos. Configurar PostgreSQL según [la guía del servidor](docs/14-postgresql-api-midori.md). Las variables `VITE_` son públicas.

```sh
npm run dev
npm run dev:server
npm run dev:electron
npm run build:web
npm run build:electron
npm run build:android
npm run build:ios
```

Cada `package.json` de plataforma ejecuta su compilación. Android necesita JDK 17, SDK 36 y Gradle; iOS requiere macOS/Xcode y el build CI es de simulador. Electron produce instaladores en el sistema correspondiente; `MIDORI_DESKTOP_FORMAT=zip` permite generar solo el ZIP en un Linux sin herramientas Debian. Builds y CI no publican ni usan credenciales de firma.

La fuente de la app es compartida. El pequeño plugin `phonegap/plugins/midori-vault` mantiene los puentes de F3 hacia Android Keystore, iOS Keychain y el selector nativo del archivo de recuperación. Estos archivos nativos son necesarios para compilar y no duplican la lógica de wallet.

Verificación general: `npm run lint` y `npm --prefix server test`. El CI incluye PostgreSQL aislado y builds web, Electron Linux/Windows/macOS, APK e iOS Simulator. No hay comandos de prueba por fase.

Updates GitHub: repositorio propio `astianx/midori-wallet`, canal estable/beta y manifiesto firmado Ed25519. Sin canal y clave configurados permanecen desactivados. La descarga es manual y no se instala código automáticamente. [Alcance, auditoría y evidencia F5](docs/18-plataformas-independientes.md).

Se conservan los recursos gráficos existentes, las [atribuciones originales](THIRD-PARTY-NOTICES.txt) y la [licencia](LICENSE). El [plan maestro](docs/01-plan-maestro-midori-wallet.md) conserva los estados y las condiciones que todavía requieren validación externa.
