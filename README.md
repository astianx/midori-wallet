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

[GitHub Actions](.github/workflows/ci.yml) builds the web app, desktop packages for Linux, macOS, and Windows, an Android debug APK, and an iOS Simulator bundle. The workflow also defines a separate, approval-gated Android release-signing job for pushes to `main`. Builds are uploaded as workflow artifacts.

Clients can check for updates on the stable or beta channel when the release repository, channel, and Ed25519 public key are configured. Downloads are manual.

## Repository layout

| Path | What lives there |
| --- | --- |
| [`web/`](web/) | Shared Vue 3 interface and Vite build configuration. |
| [`packages/wallet-core/`](packages/wallet-core/) | Wallet logic built with Tether's Wallet Development Kit (WDK). |
| [`server/`](server/) | Express API, PostgreSQL migrations, and background worker. See the [OpenAPI contract](server/openapi.json) for the API surface. |
| [`electron/`](electron/) | Desktop shell and Electron Forge packaging. |
| [`phonegap/`](phonegap/) | Cordova builds for Android and iOS. The [Midori Vault plugin](phonegap/plugins/midori-vault/) provides native access to Android Keystore, iOS Keychain, and the recovery file picker. |

## Checks

```sh
npm run lint
npm --prefix server test
```

For the PostgreSQL integration tests, use a separate test database and follow the database setup in the [CI workflow](.github/workflows/ci.yml).

## License

Midori Wallet is released under the [MIT License](LICENSE). See [third-party notices](THIRD-PARTY-NOTICES.txt) for upstream attributions.
