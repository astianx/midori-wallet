# Midori Wallet

Midori Wallet is an Android **self-custodial wallet** in development. Our goal is to combine a clear, secure wallet experience with identity, private conversations, and payments to contacts from within a chat. We are building and testing the financial experience first; messaging and integrated payments will follow.

> **Status: v0.1 in development.** The app operates only on **Ethereum Sepolia and Bitcoin testnet4**. Test assets have no monetary value and cannot be redeemed for real money. This version is not ready to hold assets of real value.

## What works today

| Area | Current state |
| --- | --- |
| Wallet | Create a 12-word recovery phrase, import an existing one, and restore a wallet saved on the device. |
| Balances | Check test USDC and ETH on Sepolia, and BTC on testnet4. USDC is the primary balance; ETH also pays Sepolia transaction fees. |
| Send and receive | Prepare USDC, ETH, and BTC transfers; display an address and QR code for the selected asset and network. The app displays a transaction hash after broadcast but does not yet verify confirmation. |
| Tools | Sign and verify messages; open broadcast transactions in the explorer for the corresponding network. |
| Interface | Dark theme, an MW monogram app icon, and a launch screen with the full brand. |

**Still needed to complete v0.1:** demonstrate funded test transfers between two clean installations, including USDC and enough ETH for gas; test a provider failure during wallet restoration; and repeat the full acceptance checks with the final APK. A successful build and emulator installation do not replace these on-chain tests.

## What we want to build

The complete experience will let people create or import a wallet, view balances and activity by asset, connect with contacts, chat, and pay them without manually copying addresses. Every payment will require an explicit review of the recipient, network, asset, amount, fee, and status. Chat may communicate that a payment was made, but **confirmation will come from checking the blockchain**, not from message delivery.

Social accounts and wallets will have separate responsibilities. An account may restore access to contacts and conversations under the service's rules, but it **will not recover the seed phrase or hold funds**. The wallet will continue to show its local balances and addresses even if the social session expires.

### Roadmap

Versions describe a sequence of work, not promised dates. The [detailed roadmap](docs/roadmap-v0.1-v2.0.md) contains acceptance criteria, risks, and technical decisions.

| Stage | Intended outcome |
| --- | --- |
| **v0.1 · Wallet foundation** | Complete and test USDC Sepolia, ETH Sepolia, and BTC testnet4 flows on two installations; distinguish broadcast from confirmation and retain the wallet through transient errors. |
| **v0.2 · Design and local security** | Improve navigation, components, copy, accessibility, and recovery phrase backup; protect secrets with Android Keystore, exclude them from backups, and migrate existing installations. |
| **v0.3 · Reliable transactions** | Add a pre-send review, network validation, fees for the exact transaction, a correct maximum spendable amount, persistent history, and confirmation tracking. Add labels and transaction filters. |
| **v0.4 · Accounts and identity** | Test Authentik, ejabberd, and PostgreSQL; add OIDC sign-in, profiles, and wallet address bindings backed by signed, revocable proof. |
| **v0.5 · One-to-one chat** | Add contacts, requests, blocking, direct messages, delivery and read states, drafts, and history that syncs after reconnection. |
| **v0.6 · Chat privacy** | Add end-to-end encryption, per-device keys, contact verification, key-change warnings, and notifications without sensitive message content. |
| **v0.7 · Payments in conversations** | Send and request payments from chat using verified wallet bindings, local review, and status cards backed by blockchain data. Receiving a message will never trigger an automatic transfer. |
| **v0.8 · Media** | Add images, files, voice notes, and one-time location sharing with contextual permissions, quotas, defined retention, and protection consistent with the chat. |
| **v0.9 · Calls** | Add one-to-one audio and video calls with camera and microphone controls, network recovery, and tests across different connection conditions. |
| **v1.0 · Integrated beta** | Deliver the complete wallet, account, chat, and payment journey on testnets, with help, recovery, monitoring, and a security review. |
| **v1.1–v1.3 · Maturity** | Add private groups, multiple devices, session revocation, accessibility, localization, load testing, and more robust operations. |
| **v2.0 · Consolidated product** | Deliver a stable wallet and messaging experience with privacy, payments, and documented operations. Version 2.0 does not by itself authorize mainnet support. |

### Product principles

- Users control their recovery phrases and authorize every transfer.
- Payment addresses must be verified; a contact name or message alone cannot establish the recipient.
- Amounts must be calculated precisely, without simulated fiat balances or promises of returns.
- Broadcast, pending, confirmed, and unknown transaction outcomes must be shown accurately.
- The app must explain the asset, network, costs, and errors before an irreversible action.
- Social features must never expose the seed phrase or let a server sign payments.

**Outside the current scope:** mainnets, fiat purchases, executable investments or swaps, server custody, cross-chain bridges, bots, and public channels. USDT and other assets will be considered only after their contract, network, and test funding path have been verified.

## Test networks

| Asset | Network | Current use |
| --- | --- | --- |
| Test USDC | Ethereum Sepolia | Primary balance and token transfers. Contract: `0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238`. |
| Test ETH | Ethereum Sepolia | USDC transaction fees and ETH transfers. |
| Test BTC | Bitcoin testnet4 | BTC balance and transfers. |

To receive assets, select the asset in the app and copy its address or QR code. USDC and ETH share an address on Sepolia, but the sender must select **USDC on Sepolia** when transferring the token. Sending USDC also requires test ETH to pay gas.

**Bitcoin testnet4 and testnet3 are separate chains**, even though their addresses may have the same prefix. Use testnet4 funds and explorers with the current configuration. Check the appropriate block explorer before treating a payment as received.

## Local development

### Requirements

- Android Studio and Android SDK 35.
- JDK 17.
- Node.js 20.19 or later.
- An Android emulator or device running API 33 or later.
- Enough disk space and connectivity to download dependencies and native resources during the first build.

### Set up, build, and run

```bash
./scripts/setup-dev.sh
./gradlew assembleDebug
./gradlew installDebug
```

Open this directory in Android Studio to work with Jetpack Compose. The first build may take longer and download several hundred megabytes. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To launch the app on a connected device:

```bash
adb shell monkey -p org.astianx.midori.one -c android.intent.category.LAUNCHER 1
```

To run the existing unit tests:

```bash
./gradlew testDebugUnitTest
```

The APK packages native libraries for compatibility with devices using 16 KB memory pages. Installation and runtime behavior on a 16 KB device still need a dedicated test before release.

## Architecture

Today the app uses **Kotlin, Jetpack Compose, Material 3, and MVVM**. `WalletViewModel.kt` owns state and operations, `Screens.kt` contains the screens, and `Components.kt` contains reusable UI elements. Local wallet storage is still basic and will be strengthened before accounts and chat are added.

The planned architecture separates use cases, encrypted local storage, and network connections. **Authentik (OIDC), ejabberd (XMPP), and PostgreSQL** will be evaluated for identity and messaging. Media and calls will require controlled storage, **WebRTC**, and **STUN/TURN** services. These components are part of the plan; **they are not integrated into the current app**.

## Security and known limitations

The current implementation stores the encryption key and encrypted seed together in `SharedPreferences`, and the manifest allows Android backups. This setup must be migrated before using the app with assets of real value or expanding it with social services. Do not store recovery phrases for valuable wallets or add more secrets to that storage.

Transaction history is not yet persistent, and the status shown after a transfer does not verify confirmations. A lost connection after broadcast may leave an uncertain outcome; check the blockchain before trying again. Fee quotes and maximum spendable amounts still need transaction-specific and network-specific validation.

Current tests cover part of the logic, and the app has been installed on an emulator. A device matrix, funded transfer tests, restoration under simulated failures, and a security review are still needed. Progress and exit criteria are tracked in the [roadmap](docs/roadmap-v0.1-v2.0.md).

## License

Apache 2.0. See [LICENSE](LICENSE).
