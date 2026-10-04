# WDK Starter Kotlin

A sample Android wallet app built with [Tether WDK](https://github.com/tetherto/wdk-core-kotlin) (Wallet Development Kit). This app demonstrates how to integrate `wdk-core-kotlin` into a Jetpack Compose Android application, supporting both **Ethereum (Sepolia testnet)** and **Bitcoin (testnet)**.

For the broader WDK ecosystem, see the [WDK documentation](https://docs.wdk.tether.io/).

## Features

- Create a new wallet with a 12-word seed phrase
- Import an existing wallet from a mnemonic
- View balances for ETH (Sepolia) and BTC (Testnet)
- Send transactions on both networks with fee estimation
- Receive funds via QR code and address display
- Sign and verify messages with your private key
- Dark theme UI built with Jetpack Compose and Material 3

## Prerequisites

- **Android Studio** (Hedgehog or newer)
- **JDK 17**
- **Android SDK 35** (compileSdk)
- **Android Emulator** or physical device running API 33+
- **Node.js ≥ 20.19** (for building the WDK worklet bundle — several transitive WDK dependencies require this minimum. Building with an older Node version fails with a confusing `rolldown` native-binding error rather than a clear version-mismatch message.)

## Project Setup

This project references `wdk-core-kotlin` as a local Gradle module. The expected directory layout:

```
Tether/
  wdk-core-kotlin/     # The WDK core library
  wdk-starter-kotlin/  # This starter app
```

### First-Time Setup

1. **Clone both repos** side by side (see the layout above).

2. **Open in Android Studio**: Open the `wdk-starter-kotlin` folder in Android Studio. Gradle sync will automatically pick up the local `wdk-core-kotlin` module.

3. **Run the app**: Select a device/emulator (API 33+) and press Run.

The first build takes a while: the `wdk-core-kotlin` module automatically installs its JS dependencies, generates the WDK worklet bundle (Node.js required), and downloads the BareKit runtime (jar + native libraries, a few hundred MB, one-time) from the [bare-kit releases](https://github.com/holepunchto/bare-kit/releases). Subsequent builds reuse all of it.

> **Note**: If Android Studio shows unresolved `to.holepunch.bare.kit` imports right after cloning, run a build once (`./gradlew assembleDebug`) — the BareKit download happens at build time, not during Gradle sync.

### Building from Terminal

```bash
./gradlew assembleDebug
```

To install on a connected device:

```bash
./gradlew installDebug
```

### Starting the Emulator

Make sure the Android SDK tools are on your `PATH` (add this to your `~/.zshrc` or shell profile):

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

List your available virtual devices and start one in the background:

```bash
emulator -list-avds
emulator -avd Pixel_9 &
```

Wait for the emulator to finish booting, then install the app:

```bash
adb wait-for-device
./gradlew installDebug
```

### Running the App

Launch the app on the emulator (or a connected device):

```bash
adb shell monkey -p to.tether.wdk.starter -c android.intent.category.LAUNCHER 1
```

## Architecture

The app follows MVVM with Jetpack Compose:

- **WalletViewModel** manages all wallet state and WDK API calls
- **Screens.kt** contains all screen composables (Welcome, Create, Import, Home, Send, Receive, Sign)
- **Components.kt** contains shared UI components (buttons, cards, QR code, seed cells)
- **MainActivity.kt** sets up the dark theme and hosts the app

## Networks

The app is configured for testnet use:

| Network | Blockchain | Provider |
|---------|-----------|----------|
| Sepolia | Ethereum (testnet) | ethereum-sepolia.publicnode.com |
| Bitcoin | Bitcoin (testnet) | blockbook.tbtc-1.zelcore.io |

## Getting Testnet Funds

New wallets start with a zero balance. Fund them using these faucets:

- **Sepolia ETH:** [Sepolia PoW Faucet](https://sepolia-faucet.pk910.de/) (no mainnet balance or social account required — mines in-browser)
- **Bitcoin testnet4:** [coinfaucet.eu (testnet4)](https://coinfaucet.eu/en/btc-testnet4/) or [Crypto Chief Testnet4 Faucet](https://crypto-chief.com/faucet/bitcoin-testnet4/)

> **Important**: this app's Bitcoin config points at **testnet4** (`blockbook.tbtc-1.zelcore.io` — confirmed via its `/api/status` endpoint, which reports `chain: testnet4`), not testnet3. The two networks are separate chains that share the same `tb1...` address format, so funding the wrong one will silently leave your balance at zero with no error. Common faucets like `coinfaucet.eu/en/btc-testnet/` and `bitcoinfaucet.uo1.net` serve **testnet3** — use a testnet4-specific faucet instead, such as the ones linked above.

Verify funds landed using a block explorer before trusting the in-app balance, since ETH confirms in seconds but BTC testnet blocks can take much longer and are inconsistent:

- ETH: [sepolia.etherscan.io](https://sepolia.etherscan.io)
- BTC: [blockbook.tbtc-1.zelcore.io](https://blockbook.tbtc-1.zelcore.io) (the same testnet4 indexer the app itself queries)

## Security Notes

This starter app is a **reference implementation for integrating `wdk-core-kotlin`**, not a production-hardened wallet. The following simplifications are intentional design choices for sample-app clarity, not oversights, and are documented here so they are not mistaken for undisclosed vulnerabilities:

- **Key storage**: the encryption key and the encrypted seed are both stored together in Android `SharedPreferences` (`wdk_wallet`). This is not equivalent to hardware-backed key storage.
- **Backups**: `android:allowBackup="true"` is set in the manifest. Combined with the storage choice above, both the encryption key and the encrypted seed could be included in the same Android backup (e.g. via Auto Backup or `adb backup`).

Production integrations should use `EncryptedSharedPreferences` backed by the Android Keystore, and should independently review their backup policy (e.g. `android:allowBackup="false"` or explicit backup-rule exclusions) rather than relying on this starter's configuration.

## Known Limitations

- **Transaction status**: the app shows "Transaction broadcast!" once `sendTransaction` returns a hash. This confirms the transaction was successfully submitted to the network — it does **not** mean the transaction has been mined or confirmed on-chain. Always verify actual confirmation via a block explorer for anything beyond casual testing.
- **Restore failure handling**: a failed wallet restore currently clears saved credentials on any error, including transient network failures, not only invalid/corrupt credentials. This is tracked as a follow-up (see open GitHub issues in this repository).

## License

See the LICENSE file for details.
