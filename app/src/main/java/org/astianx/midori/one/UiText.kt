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
