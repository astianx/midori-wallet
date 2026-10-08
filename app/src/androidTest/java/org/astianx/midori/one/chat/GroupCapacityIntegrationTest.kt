package org.astianx.midori.one.chat

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.jivesoftware.smack.android.AndroidSmackInitializer
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Diagnostic mode: all clients in one Android process. Prefer the distributed runner at 200. */
@RunWith(AndroidJUnit4::class)
class GroupCapacityIntegrationTest {
    @Test fun fullCapacityOfflineRecoveryRemovalAndNoProtocolFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.filesDir, "group-capacity-accounts.json")
        assumeTrue(fixture.exists())
        AndroidSmackInitializer.initialize(context)
        GroupCapacityScenario(JSONObject(fixture.readText()), context.filesDir, "10.0.2.2",
            { android.util.Log.i("MidoriGroupTest", it) }, { Debug.getPss().toLong() }).run()
    }
}
