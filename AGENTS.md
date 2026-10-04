# Agent Guide

This repository is part of the Tether WDK (Wallet Development Kit) ecosystem. It is a sample Android wallet app demonstrating how to integrate `wdk-core-kotlin` into a Jetpack Compose application, supporting Ethereum (Sepolia testnet) and Bitcoin (testnet).

## Project Overview
- **Architecture:** MVVM with Jetpack Compose. A single `WalletViewModel` owns all wallet state and WDK API calls; composable screens observe it via `StateFlow`.
- **Runtime:** Android (minSdk 33, targetSdk 35, compileSdk 35). The WDK core runs its JavaScript worklet under the hood via the `wdk-core-kotlin` module.

## Tech Stack & Tooling
- **Language:** Kotlin (JVM target 17).
- **UI:** Jetpack Compose with Material 3 (Compose BOM 2024.02.00), dark theme only.
- **Build System:** Gradle (Groovy DSL).
  - Build: `./gradlew assembleDebug`
  - Install on device/emulator: `./gradlew installDebug`
- **Wallet SDK:** `wdk-core-kotlin`, referenced as a local Gradle module expected at `../wdk-core-kotlin` (see `settings.gradle`).
- **QR Codes:** `com.google.zxing:core`.
- **JDK:** 17 is required.

## Coding Conventions
- **File Naming:** PascalCase for Kotlin files (e.g., `WalletViewModel.kt`, `Screens.kt`).
- **Class Naming:** PascalCase (e.g., `WalletViewModel`, `PendingTx`).
- **Package:** All code lives under `to.tether.wdk.starter`.
- **State:** UI state is modeled as immutable data classes (`WalletUiState`) exposed through `StateFlow`; mutations go through `MutableStateFlow.update`-style copy helpers in the ViewModel.
- **Composables:** Screen-level composables live in `Screens.kt`; reusable UI pieces live in `Components.kt`. Follow this split when adding UI.
- **Async:** Use `viewModelScope` coroutines for WDK calls; never block the main thread.
- **License:** The project is licensed under Apache 2.0 (see `LICENSE`).

## Development Workflow
1.  **Build the WDK bundle** (first time only): `cd ../wdk-core-kotlin/js && npm install && npm run generate`
2.  **Sync:** Open in Android Studio (Hedgehog or newer); Gradle sync picks up the local `wdk-core-kotlin` module.
3.  **Build:** `./gradlew assembleDebug`
4.  **Run:** `./gradlew installDebug` on a device/emulator running API 33+.

## Key Files
- `app/src/main/java/to/tether/wdk/starter/MainActivity.kt`: Entry point; sets up the dark theme and hosts the app.
- `app/src/main/java/to/tether/wdk/starter/WalletViewModel.kt`: All wallet state, WDK initialization, balances, transactions, signing.
- `app/src/main/java/to/tether/wdk/starter/Screens.kt`: Screen composables (Welcome, Create, Import, Home, Send, Receive, Sign).
- `app/src/main/java/to/tether/wdk/starter/Components.kt`: Shared UI components (buttons, cards, QR code, seed cells).
- `settings.gradle`: Wires in the local `wdk-core-kotlin` module.

## Repository Specifics
- **Domain:** Multi-chain wallet demo app (Ethereum + Bitcoin).
- **Networks:** Testnet only — Sepolia (`ethereum-sepolia.publicnode.com`) and Bitcoin testnet (`blockbook.tbtc-1.zelcore.io`). Network identifiers passed to WDK are `"sepolia"` and `"bitcoin"`.
- **Key Library:** `WdkCore` from `wdk-core-kotlin` — wallet creation/import (12-word mnemonic), address derivation, balance queries, fee estimation, sending transactions, message signing/verification.
- **Persistence:** The encryption key and encrypted seed are stored in `SharedPreferences` (`wdk_wallet`). This is sample-app simplicity, not a production pattern — do not extend it with additional secrets without flagging the security implications.
- **Explorers:** Confirmed transactions link to `sepolia.etherscan.io` (ETH) and `blockbook.tbtc-1.zelcore.io` (BTC).
- **Testing:** No test suite is currently configured; verify changes by building (`./gradlew assembleDebug`) and running the app on an API 33+ emulator or device.
