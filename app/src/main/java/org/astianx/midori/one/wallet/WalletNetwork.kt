package org.astianx.midori.one.wallet

import org.astianx.midori.one.UiText

enum class Network(val wdkName: String, val label: String, val explorer: String) {
    BASE("base", "Base", "https://basescan.org"),
    BTC("bitcoin", "Bitcoin", "https://blockbook.btc.zelcore.io")
}

// Mainnet only. Contracts are pinned to their chain; see the read-only network verifier.
object BaseMainnet {
    const val CHAIN_ID = 8453
    const val RPC_URL = "https://base-rpc.publicnode.com"
    const val USDC_CONTRACT = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"

    // Bridged USDT from Ethereum via the Base standard bridge, not native USD₮ or USDT0.
    const val USDT_CONTRACT = "0xfde4C96c8593536E31F229EA8f37b2ADa2699bb2"

    // Stablecoin transfers with no ETH: the same EOA delegates (EIP-7702) to the audited eth-infinitism
    // Simple7702Account for EntryPoint v0.8, and the Candide paymaster takes the fee in USDC/USDT.
    const val GASLESS_NETWORK = "base-gasless"
    const val GASLESS_BUNDLER_URL = "https://api.candide.dev/public/v3/$CHAIN_ID"
    const val GASLESS_DELEGATION_ADDRESS = "0xe6Cae83BdE06E4c305530e199D7217f42808555B"
    const val GASLESS_PAYMASTER_ADDRESS = "0xa8151918eac3818deb713d3dbbb7930329fe86ed"

    // Stablecoin base units (6 decimals): 0.5. A quote at or above this is refused before signing.
    const val GASLESS_MAX_FEE = 500_000L

    val wdkConfig = """
        {"networks": {
          "base": {"blockchain": "base", "config": {
            "chainId": $CHAIN_ID, "provider": "$RPC_URL"
          }},
          "$GASLESS_NETWORK": {"blockchain": "$GASLESS_NETWORK", "config": {
            "chainId": $CHAIN_ID, "provider": "$RPC_URL", "entryPointVersion": "0.8",
            "bundlerUrl": "$GASLESS_BUNDLER_URL", "delegationAddress": "$GASLESS_DELEGATION_ADDRESS",
            "paymasterAddress": "$GASLESS_PAYMASTER_ADDRESS", "paymasterToken": {"address": "$USDC_CONTRACT"},
            "transferMaxFee": $GASLESS_MAX_FEE
          }},
          "bitcoin": {"blockchain": "bitcoin", "config": {
            "client": {"type": "blockbook-http", "clientConfig": {
              "url": "${BitcoinMainnet.API_URL}"
            }}, "network": "bitcoin"
          }}
        }}
    """.trimIndent()
}

object BitcoinMainnet {
    const val API_URL = "https://blockbook.btc.zelcore.io/api"
    const val GENESIS_HASH = "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"
}

enum class Asset(
    val network: Network,
    val decimals: Int,
    val isToken: Boolean = false,
    val contract: String? = null,
    val unavailableReason: UiText? = null
) {
    USDC(Network.BASE, 6, isToken = true, contract = BaseMainnet.USDC_CONTRACT),
    USDT(Network.BASE, 6, isToken = true, contract = BaseMainnet.USDT_CONTRACT),
    BTC(Network.BTC, 8),
    ETH(Network.BASE, 18);

    val isAvailable: Boolean get() = unavailableReason == null && (!isToken || contract != null)
}

/** Exchanges may paste a payment URI literally. Receive QR codes contain only the wallet address. */
internal fun receiveRequest(asset: Asset, address: String): String? {
    if (!asset.isAvailable || address.isBlank()) return null
    return when (asset.network) {
        Network.BASE -> {
            if (!address.matches(Regex("0x[a-fA-F0-9]{40}"))) return null
            address
        }
        // The mainnet address encodes the Bitcoin network. WDK validates its checksum on send.
        Network.BTC -> if (isBitcoinMainnetAddress(address)) address else null
    }
}

/** Network/shape guard; checksum validation is performed by WDK when quoting a send. */
internal fun isBitcoinMainnetAddress(address: String): Boolean =
    address.matches(Regex("[13][a-km-zA-HJ-NP-Z1-9]{25,34}")) ||
        address.matches(Regex("bc1[ac-hj-np-z02-9]{11,71}")) ||
        address.matches(Regex("BC1[AC-HJ-NP-Z02-9]{11,71}"))
