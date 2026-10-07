package org.astianx.midori.one.chat

import android.content.Context
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** One encrypted session per process, shared by the activity and its foreground service. */
internal object ChatSession : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var initialized = false

    @MainThread
    fun get(context: Context): ChatViewModel {
        val model = ViewModelProvider(this, ChatViewModel.Factory(context.applicationContext))[ChatViewModel::class.java]
        if (!initialized) {
            initialized = true
            model.setForeground(false)
        }
        return model
    }
}
