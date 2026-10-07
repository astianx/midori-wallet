package org.astianx.midori.one.wallet

import androidx.annotation.StringRes
import org.astianx.midori.one.UiText

/** Expected failures carry a localizable message instead of provider/SDK prose. */
internal class WalletException(val userMessage: UiText) : IllegalArgumentException()

internal fun walletRequire(condition: Boolean, @StringRes message: Int) {
    if (!condition) throw WalletException(UiText(message))
}

internal fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    (this as? WalletException)?.userMessage ?: UiText(fallback)
