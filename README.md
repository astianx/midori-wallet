# Midori Wallet

Midori Wallet is an open-source, self-custodial wallet for sending and receiving USDT and USDC on Base. The web, desktop, and mobile apps share one Vue interface. Transactions are signed on your device, and you can restore a wallet using its recovery phrase and recovery file.

When payments are enabled, the wallet quotes the network fee in the token you send. A USDC transfer, for example, shows the network fee in USDC. Midori adds no transfer fee of its own.

## Run locally

Use Node.js 22.22.2 (pinned in [`.nvmrc`](.nvmrc)), npm 10–12, and a running PostgreSQL instance.

```sh
git clone https://github.com/astianx/midori-wallet.git
cd midori-wallet
npm ci --ignore-scripts
npm --prefix packages/wallet-core ci --ignore-scripts
npm --prefix web ci --ignore-scripts
npm --prefix server ci --ignore-scripts
```

Copy the root [environment example](.env.example) to `.env`. Replace the two URL values in that file with the local web and API addresses.

```dotenv
VITE_SITE_URL=http://localhost:5173/
VITE_API_ORIGIN=http://127.0.0.1:8080
```

On a fresh local PostgreSQL instance, set `MIDORI_PG_ADMIN_URL` in your shell to a connection URL for a role that can create databases and roles. Then run the bootstrap command.

```sh
npm --prefix server run db:bootstrap
```

Bootstrap creates the `midori_wallet` database, applies the migrations, and writes separate runtime and migration credentials to `server/.env` and `server/.env.migrations`. To apply later migrations, run `npm run db:migrate` from the repository root. The [server environment example](server/.env.example) lists the API settings and their defaults. Keep credentials in server-side configuration and out of Git. Values prefixed with `VITE_` can be exposed to the client.

Start the API and the Vite dev server in separate terminals.

```sh
npm run dev:server
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). The API listens on port 8080 by default. This setup targets Base mainnet and leaves transaction submission and gas sponsorship disabled. Payment provider settings live in the [server environment example](server/.env.example).

## Build for each platform

| Target | Command | Requirements and output |
| --- | --- | --- |
| Web | `npm run build:web` | Writes the web bundle to `server/dist`. |
| Desktop | `npm run build:electron` | Packages the app for the host OS. On Linux, Debian packaging uses `dpkg` and `fakeroot`. Set `MIDORI_DESKTOP_FORMAT=zip` for a ZIP-only build. |
| Android | `npm run build:android` | Requires JDK 17, Android SDK 36, Build Tools 36.0.0, and Gradle. CI uses Gradle 8.14.2. |
| iOS | `npm run build:ios` | Requires macOS and Xcode. CI builds for the iOS Simulator. |

Install the target-specific dependencies before building desktop or mobile.

```sh
# Desktop
npm --prefix electron ci --ignore-scripts
node electron/node_modules/electron/install.js

# Android and iOS
npm --prefix phonegap ci --ignore-scripts
```

After a desktop build, run `npm run dev:electron` to launch the built client. Native targets can override the root settings through their [Electron](electron/.env.example) or [Cordova](phonegap/.env.example) environment files.

[GitHub Actions](.github/workflows/ci.yml) builds the web app, desktop packages for Linux, macOS, and Windows, an Android debug APK, and an iOS Simulator bundle. Signed Android APK/AAB builds run automatically on pushes to `main`, or manually on `main` with the `android_release` option, using the `release-signing` environment. Other branches and pull requests only produce validation builds. Artifacts include version, commit and SHA-256 manifests. See the [CI/CD process and troubleshooting guide](.github/README.md) for shared toolchains, signing requirements and manual execution.

Pushing a version tag (`vX.Y.Z`, or `vX.Y.Z-beta.N` for the beta channel) additionally builds release-profile desktop installers, signs the Android APK/AAB, and publishes a **draft** GitHub release with every installer plus an Ed25519-signed update manifest (`midori-updates-<channel>.json` + `.sig`). A human must review and publish the draft; `createGitHubUpdates` in `packages/update-core` ignores draft releases, so nothing auto-updates until then.

Clients can check for updates on the stable or beta channel when the release repository, channel, and Ed25519 public key are configured. Downloads are manual.

## Repository layout

| Path | What lives there |
| --- | --- |
| [`web/`](web/) | Shared Vue 3 interface and Vite build configuration. |
| [`packages/wallet-core/`](packages/wallet-core/) | Wallet logic built with Tether's Wallet Development Kit (WDK). |
| [`server/`](server/) | Express API, PostgreSQL migrations, and background worker. See the [OpenAPI contract](server/openapi.json) for the API surface. |
| [`electron/`](electron/) | Desktop shell and Electron Forge packaging. |
| [`phonegap/`](phonegap/) | Cordova builds for Android and iOS. The [Midori Vault plugin](phonegap/plugins/midori-vault/) provides native access to Android Keystore, iOS Keychain, and the recovery file picker. |

## Transak buy and sell

The existing Buy/Sell screens use `@transak/wdk-protocol-fiat-transak` on the server. Configure `MIDORI_TRANSAK_ENABLED=true`, `MIDORI_TRANSAK_ENVIRONMENT=STAGING`, `MIDORI_TRANSAK_API_KEY`, `MIDORI_TRANSAK_API_SECRET` and `MIDORI_TRANSAK_REFERRER_DOMAIN` in `server/.env`, then run `npm run db:migrate`. The secret and partner access token remain server-side. API and workers coordinate token renewal through PostgreSQL because refreshing a token invalidates its predecessor.

The screens load the provider's available assets, fiat currencies and the API key's enabled products before opening a session. SELL must be enabled in the Transak partner dashboard; an API key with only BUY cannot open sell sessions. Partner permissions refresh after 30 seconds. Staging currently offers USDC on Base Sepolia (84532), with 18 decimals; USDT on Base is absent from its coverage. These test orders do not change Midori's Base mainnet balances. The widget handles quotes, payment methods and KYC. For staging sell tests use test funds on the network shown by Transak; Midori's Send screen sends on Base mainnet.

For the prepared local instance, run `npm --prefix server run server:transak-staging` and `npm --prefix server run worker:transak-staging`; they read the private `server/.env.transak-staging` and serve the compiled app at `http://localhost:8081`.

Keep `npm --prefix server run worker` running for order reconciliation. It searches both BUY and SELL orders by the exact partner order ID, then fetches the order through WDK. Configure `POST /api/v1/webhooks/transak` in the partner dashboard for signed updates when Transak has approved webhooks. A local instance uses polling without requiring public webhook access. The UI refreshes pending orders while visible and on returning from Transak. Session URLs expire after five minutes and can be used once.

For a loopback reverse proxy that overwrites `X-Forwarded-For`, set `MIDORI_TRUST_PROXY=true`. It defaults to false; arbitrary client forwarding headers are ignored. Only enable PRODUCTION with its matching credentials and verified Base mainnet asset coverage.

## WalletConnect

Set `VITE_WALLETCONNECT_ID` in the root `.env` (shared by web, Electron and Cordova), then restart the development server or rebuild the client. Configure the production origins/application IDs in the WalletConnect Dashboard allowlist. The Project ID is public client configuration, not a signing key.

For GitHub Actions, create the repository variable `WALLETCONNECT_PROJECT_ID` under **Settings → Secrets and variables → Actions → Variables**. The workflow maps it to `VITE_WALLETCONNECT_ID`; no Project ID is stored in the workflow or environment examples.

In a dApp, select Base and copy its WalletConnect v2 URI or display its QR. In Midori, open **Settings → Connections → WalletConnect**, paste or scan it, then review the domain, account and requested permissions before approving. Opening `/wc?uri=<encoded WalletConnect URI>` from a dApp, or scanning its QR, starts pairing and opens permission review automatically. Entering from Settings without a dApp request keeps the manual connection form. Each signature or payment requires a separate approval and PIN. Sessions can be disconnected from this screen; locking Midori disconnects them too.

Supported: Base mainnet (`eip155:8453`), `personal_sign`, EIP-712 typed data, and USDT/USDC ERC-20 transfers through Midori's existing quoted payment flow. Transfers require the configured payment API and fee provider; this integration does not enable disabled payments or sponsorship. Arbitrary calls, token approvals, native ETH transfers and other chains are rejected. Smart account signatures use ERC-1271 and ERC-6492 for undeployed accounts; the dApp must support contract wallets. Keep the WalletConnect screen open to review incoming requests.

## Checks

```sh
npm run lint
npm --prefix web run check:i18n
npm --prefix server test
npm --prefix server run contract:build
git diff --exit-code -- server/openapi.json
npm run build:web
```

For the PostgreSQL integration tests, use a separate test database and follow the database setup in the [CI workflow](.github/workflows/ci.yml).

When adding or changing interface messages, update every language registered in `web/src/lib/i18n/languages.js`. The translation check verifies matching keys, nonempty text, interpolation variables and message syntax in every catalog; CI runs it before building.

Asian interface fonts are bundled locally under `web/public/fonts` so Khmer, Thai, Japanese, Korean and Chinese remain readable without installed system fonts or runtime font downloads. If a new translation introduces characters outside a font subset, run `npm --prefix web run fonts:update` and include the updated fonts and coverage manifest. The locale check verifies this coverage too.

CI validates code, all language catalogs, unit and PostgreSQL integration tests, the API contract and production builds.

Before publishing, validate the running app: `/healthz` and `/readyz` must return HTTP 200; open a wallet, connect a Base dApp through WalletConnect, approve its permissions, confirm a signature with the PIN, reject a request and disconnect. Keep functional validation scripts and reports outside this repository and leave local services running for review.

## License

Midori Wallet is released under the [MIT License](LICENSE). See [third-party notices](THIRD-PARTY-NOTICES.txt) for upstream attributions.
