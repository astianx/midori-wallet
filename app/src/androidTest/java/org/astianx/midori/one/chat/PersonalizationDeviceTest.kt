package org.astianx.midori.one.chat

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astianx.midori.one.LauncherIcons
import org.astianx.midori.one.LauncherStyle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersonalizationDeviceTest {
    @Test fun eachIconLeavesExactlyOneLaunchableEntryAndRestoresThePreviousChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = LauncherIcons.current(context)
        try {
            LauncherStyle.entries.forEach { style ->
                LauncherIcons.select(context, style)
                val matches = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                assertEquals(1, matches.size)
                assertEquals("${context.packageName}.${style.component}", matches.single().activityInfo.name)
                assertEquals(style, LauncherIcons.current(context))
            }
        } finally { LauncherIcons.select(context, before) }
    }
    @Test fun selectedArtworkIsResizedStoredPrivatelyAndRejectsTraversal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "midori-artwork-test-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        val store = ChatArtwork(context)
        var local: String? = null
        try {
            val source = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
            source.eraseColor(android.graphics.Color.GREEN)
            resolver.openOutputStream(uri)!!.use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }; source.recycle()
            local = store.import(uri)
            val file = store.file(local)
            assertTrue(file.canonicalPath.startsWith(context.filesDir.canonicalPath + "/chat-artwork/"))
            val image = BitmapFactory.decodeFile(file.path)
            assertEquals(1600, image.width); assertEquals(800, image.height); image.recycle()
            assertTrue(runCatching { store.file("../../secret") }.isFailure)
            assertTrue(runCatching { store.import(android.net.Uri.parse("file:///data/secret")) }.isFailure)
        } finally { local?.let { store.file(it).delete() }; resolver.delete(uri, null, null) }
    }
}
