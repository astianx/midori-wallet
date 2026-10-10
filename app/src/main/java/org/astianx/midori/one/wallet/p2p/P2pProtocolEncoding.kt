package org.astianx.midori.one.wallet.p2p

import org.bouncycastle.jcajce.provider.digest.Keccak

/** Shared protocol primitives. No SDK transport, Android persistence or relay identity. */
internal fun ByteArray.p2pHex() = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun p2pBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0 && hex.matches(Regex("[a-fA-F0-9]*")))
    return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
internal fun p2pKeccak(bytes: ByteArray) = Keccak.Digest256().digest(bytes)
