# Messaging dependencies

The project-authored source retains Apache-2.0. The messaging integration includes `smack-omemo-signal`, which has a different license. This does **not** relicense third-party code or eliminate its requirements. Distribution of the combined APK must comply with GPLv3 and include corresponding source and applicable notices. Do not describe that APK as exclusively Apache-2.0.

| Component | Pinned version | License | Source |
| --- | --- | --- | --- |
| Smack core, Android, TCP, extensions, experimental and OMEMO core | 4.5.0 | Apache-2.0 | https://github.com/igniterealtime/Smack/tree/4.5.0 |
| Smack OMEMO Signal adapter | 4.5.0 | GPL-3.0-or-later | https://github.com/igniterealtime/Smack/tree/4.5.0/smack-omemo-signal |
| libsignal-protocol-java | 2.8.1 | GPL-3.0 | https://github.com/signalapp/libsignal-protocol-java/tree/v2.8.1 |

Original notices and license texts are in `app/src/main/assets/licenses/`. More → Open source licenses displays messaging notices in the app. Existing wallet dependencies keep their own licenses. Conversations is a behavioral reference; its source has not been copied into this project.


## Manrope

Manrope is bundled under the SIL Open Font License 1.1. The complete notice is in `app/src/main/assets/licenses/Manrope-OFL.txt` and in the in-app licenses screen. Source: https://github.com/google/fonts/tree/main/ofl/manrope.

## MLS qualification laboratory (host only)

`tools/mls-lab` uses OpenMLS 0.9.1 and the OpenMLS RustCrypto, basic-credential and traits crates 0.6.0 under MIT. `Cargo.lock` pins their transitive dependencies; original notices are in the downloaded Cargo source packages. Source: https://github.com/openmls/openmls. These dependencies are not linked into the Android APK. See `tools/mls-lab/README.md` for scope and distribution requirements. They do not change the existing APK's Signal OMEMO license obligations.

## Transak backend (server only)

`services/fiat` uses `@transak/wdk-protocol-fiat-transak` 1.0.2 (Apache-2.0), `ethers` 6.17.0 (MIT) and `bitcoinjs-lib` 7.0.2 (MIT). Its package-lock.json pins transitive dependencies; preserve original notices supplied in the installed packages when distributing the service. These packages are not linked into the Android APK and do not alter its existing Signal OMEMO obligations.

## P2P.me backend (server only)

`services/fiat` additionally pins `@p2pdotme/sdk` 1.3.7 (MIT) and `viem` 2.47.6 (MIT). The original SDK notice is retained in `services/fiat/licenses/p2pdotme-sdk-MIT.txt`. Source: https://github.com/p2pdotme/p2pdotme-sdk/tree/d4819577953bf21397e931b5e4bac2ea44251f2c. Preserve installed transitive notices when distributing the backend; do not describe every transitive package as MIT. The backend uses public country/price/fee reads and unsigned order preparation, routing, history and receipt reads. SDK-derived ABIs in `services/fiat/abi` retain this MIT notice. Browser fraud, SEON, React and KYC integrations are not activated, and the SDK is not linked into the Android APK.

## P2P.me subgraph (indexing infrastructure only)

`tools/p2p-subgraph/midori-wallet` contains the upstream MIT-licensed indexer at revision `ef6145bcf44e6126ce89f1cbc1e6759a2ec8d9b9`, with Midori package/deployment names. Original license: `tools/p2p-subgraph/midori-wallet/LICENSE`; provenance and source hashes: `UPSTREAM.json` in the same directory. Source: https://github.com/p2pdotme/subgraph. Its pinned Graph CLI/Graph TS toolchain and transitive dependencies keep their own notices. It is deployed separately to The Graph, not bundled into Android or the Cloudflare fiat Worker. Midori's inspection and endpoint-check scripts retain Apache-2.0.


## Native P2P payment interoperability

Android implements the SDK 1.3.7 payment-ID field order and a conservative subset of its validators, plus its existing ECIES wire format. Original SDK MIT notice is bundled in `app/src/main/assets/licenses/p2pdotme-sdk-MIT.txt`. Bouncy Castle `bcprov-jdk18on` 1.80.2 supplies secp256k1 and Keccak primitives; its original license from the pinned source artifact is in `BouncyCastle-LICENSE.txt` in that directory. Both notices appear in the in-app licenses screen. These additions do not change the Signal OMEMO obligations. No P2P relay identity derives from wallet/chat keys.

## Emoji picker and contact QR scanner

AndroidX Emoji2, its emoji picker and bundled font adapter are pinned to 1.5.0 (Apache-2.0). The bundled Noto Color Emoji font retains its own OFL notice; the adapter notice is preserved in `app/src/main/assets/licenses/Emoji2-Bundled-LICENSE.txt`, and the font copyright/OFL text in `NotoColorEmoji-OFL.txt`. Fonts are loaded from the APK, without a font-provider request. Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-emoji2-release/emoji2/.

JourneyApps ZXing Android Embedded 4.3.0 and ZXing Core 3.5.3 supply the contact QR reader/encoder (Apache-2.0). Source: https://github.com/journeyapps/zxing-android-embedded/tree/v4.3.0 and https://github.com/zxing/zxing/tree/zxing-3.5.3. The application validates and reviews contact data before saving it; scanning does not execute a QR action. Preserve their packaged notices when distributing.
