package org.astianx.midori.one

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Resolve at the UI boundary so retained wallet state follows locale changes. */
data class UiText(@StringRes val resourceId: Int, val arguments: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(resourceId, *arguments.toTypedArray())

    @Composable
    fun asString(): String = stringResource(resourceId, *arguments.toTypedArray())
}

/** Expected failures carry a localizable message instead of provider/SDK prose. */
internal class WalletException(val userMessage: UiText) : IllegalArgumentException()

internal fun walletRequire(condition: Boolean, @StringRes message: Int) {
    if (!condition) throw WalletException(UiText(message))
}

internal fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    (this as? WalletException)?.userMessage ?: UiText(fallback)
