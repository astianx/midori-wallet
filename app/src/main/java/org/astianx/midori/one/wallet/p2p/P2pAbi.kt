package org.astianx.midori.one.wallet.p2p

import java.math.BigInteger
import org.astianx.midori.one.wallet.BaseMainnet
import org.astianx.midori.one.wallet.fiat.FiatOperation

/** Canonical local encoding of permitted P2P calls; no arbitrary provider calldata. */
internal object P2pAbi {
    private data class Arg(val value: String, val dynamic: Boolean = false)
    private fun uint(value: String): Arg {
        require(p2pInteger(value)); return Arg(BigInteger(value).toString(16).padStart(64, '0'))
    }
    private fun address(value: String): Arg {
        require(value.matches(Regex("0x[a-fA-F0-9]{40}"))); return Arg(value.substring(2).lowercase().padStart(64, '0'))
    }
    private fun text(value: String): Arg {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return Arg(uint(bytes.size.toString()).value + bytes.p2pHex().padEnd((bytes.size + 31) / 32 * 64, '0'), true)
    }
    private fun call(signature: String, vararg args: Arg): String {
        var tail = ""
        val head = args.joinToString("") { arg ->
            if (!arg.dynamic) arg.value else {
                val offset = uint((args.size * 32 + tail.length / 2).toString()).value
                tail += arg.value; offset
            }
        }
        return "0x" + p2pKeccak(signature.toByteArray()).copyOfRange(0, 4).p2pHex() + head + tail
    }
    fun place(quote: P2pOrderQuote): String {
        require(quote.currency.matches(Regex("[A-Z]{3}")) && quote.relayPublicKey.matches(Regex("[a-f0-9]{128}")))
        require(p2pInteger(quote.amount, true) && p2pInteger(quote.fiatUnits, true) && p2pInteger(quote.price, true) &&
            p2pInteger(quote.totalUsdc, true) && p2pInteger(quote.estimatedFiatTotal, true) && p2pInteger(quote.fee))
        val buy = quote.operation == FiatOperation.BUY
        require(quote.fiatUnits.toBigInteger() == quote.amount.toBigInteger() * quote.price.toBigInteger() / BigInteger.valueOf(1_000_000))
        require(quote.totalUsdc.toBigInteger() == quote.amount.toBigInteger() + if (buy) BigInteger.ZERO else quote.fee.toBigInteger())
        require(quote.estimatedFiatTotal.toBigInteger() == quote.fiatUnits.toBigInteger() +
            if (buy) quote.fee.toBigInteger() * quote.price.toBigInteger() / BigInteger.valueOf(1_000_000) else BigInteger.ZERO)
        return call("placeOrder(string,uint256,address,uint8,string,string,bytes32,uint256,uint256,uint256)",
            text(if (buy) quote.relayPublicKey else ""), uint(quote.amount), address(quote.user), uint(if (buy) "0" else "1"),
            text(""), text(if (buy) "" else quote.relayPublicKey), Arg(quote.currency.toByteArray().p2pHex().padEnd(64, '0')),
            uint("0"), uint(quote.circle), uint(quote.fiatUnits))
    }
    fun action(action: P2pAction, id: String, approval: String = "0", encrypted: String? = null,
        evidenceId: String? = null): Pair<String, String> = when (action) {
        P2pAction.APPROVE -> BaseMainnet.USDC_CONTRACT to call("approve(address,uint256)", address(P2P_DIAMOND), uint(approval))
        P2pAction.HANDOFF -> P2P_DIAMOND to call("setSellOrderUpiWithFiat(uint256,string,uint256)", uint(id), text(checkNotNull(encrypted)), uint("0"))
        P2pAction.PAID -> P2P_DIAMOND to call("paidBuyOrder(uint256)", uint(id))
        P2pAction.CANCEL -> P2P_DIAMOND to call("cancelOrder(uint256)", uint(id))
        P2pAction.DISPUTE -> P2P_DIAMOND to call("raiseDispute(uint256,uint256)", uint(id), uint(checkNotNull(evidenceId)))
        P2pAction.PLACE -> error("A placement requires its bound quote")
    }
}
