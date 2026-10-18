package org.astianx.midori.one.wallet.p2p

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.astianx.midori.one.MidoriTheme
import org.astianx.midori.one.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory identities and isolated Keystore aliases. Never uses the Pixel wallet or a real passport. */
@RunWith(AndroidJUnit4::class)
class P2pKycDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun englishVerificationReviewAndConfirmation() = flow("en")
    @Test fun spanishVerificationReviewAndConfirmation() = flow("es")
    @Test fun portugueseVerificationReviewAndConfirmation() = flow("pt")
    @Test fun anUnknownBroadcastRequiresHashRecoveryAndDoesNotOfferAnotherSubmission() = flow("es", true)
    @Test fun englishRefusalsHaveNoRetryOrSubmission() = refusals("en")
    @Test fun spanishRefusalsHaveNoRetryOrSubmission() = refusals("es")
    @Test fun portugueseRefusalsHaveNoRetryOrSubmission() = refusals("pt")
    private fun refusals(language: String) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = target.createConfigurationContext(config)
        var stage by mutableStateOf("REJECTED")
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                MidoriTheme { P2pKycContent(P2pKycState(P2pKycFixture().owner, "CO", stage), {}, {}, {}, {}, {}, {}, {}, {}) }
            }
        }
        for ((value, label) in listOf("REJECTED" to R.string.p2pk_rejected, "DUPLICATE_IDENTITY" to R.string.p2pk_duplicate)) {
            compose.runOnUiThread { stage = value }
            compose.onNodeWithText(context.getString(label)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.p2pk_start)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.p2pk_submit)).assertDoesNotExist()
        }
    }
    private fun flow(language: String, loseHash: Boolean = false) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = target.createConfigurationContext(config)
        val fixture = P2pKycFixture().apply { failAfterBroadcast = loseHash }
        val models = ViewModelStore(); lateinit var vm: P2pKycViewModel
        compose.runOnUiThread { vm = fixture.viewModel(); models.put("kyc", vm); vm.refresh() }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                    MidoriTheme {
                        val state by vm.state.collectAsState()
                        P2pKycContent(state, {}, vm::start, vm::reopen, vm::refresh, vm::prepare, vm::submit, vm::dismiss, vm::recover)
                    }
                }
            }
            fun click(resource: Int) = compose.onNodeWithText(context.getString(resource)).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.p2pk_start)).performScrollTo().assertIsNotEnabled()
            compose.onNode(isToggleable()).performScrollTo().performClick(); click(R.string.p2pk_start)
            compose.waitForIdle(); assertEquals(1, fixture.sessions); assertEquals(0, fixture.emissions)
            compose.runOnUiThread { vm.launched(); fixture.stage = "READY" }; click(R.string.p2po_refresh)
            compose.onNodeWithText(context.getString(R.string.p2pk_ready)).performScrollTo().assertIsDisplayed()
            click(R.string.p2pk_prepare); compose.onNodeWithTag("p2pk-review").assertExists()
            assertEquals(0, fixture.emissions); click(R.string.p2pk_submit); compose.waitForIdle(); assertEquals(1, fixture.emissions)
            if (loseHash) {
                compose.onNodeWithTag("p2pk-recovery-hash").performScrollTo().performTextInput(fixture.hash)
                compose.onNodeWithText(context.getString(R.string.p2pk_submit)).assertDoesNotExist()
                click(R.string.p2po_recover)
            } else click(R.string.p2po_refresh)
            compose.onNodeWithText(context.getString(R.string.p2pk_confirmed)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.p2po_account_limit, "100")).assertExists()
            assertEquals(1, fixture.emissions)
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(context.cacheDir, "p2p-kyc-$language-${if (loseHash) "recovery" else "confirmed"}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { compose.runOnUiThread { models.clear() } }
    }
    @Test fun isolatedKycJournalSurvivesRestartAndRejectsStaleWritersCorruptionAndAccountSubstitution() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "p2p-kyc-fixture-${UUID.randomUUID()}"; val alias = "midori.p2p.kyc.fixture.${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val f = P2pKycFixture(); val row = P2pKycRecord(f.owner, "CO", "a".repeat(64), "b".repeat(64),
            state = "UNKNOWN", proof = f.proof, data = P2pKycEncoding.calldata(f.proof))
        try {
            val first = KeystoreP2pKycStore(context, name, alias); val stale = KeystoreP2pKycStore(context, name, alias)
            assertNull(first.load(f.owner)); assertNull(stale.load(f.owner)); first.save(f.owner, row)
            val envelope = checkNotNull(prefs.getString(f.owner, null)); assertFalse(envelope.contains(row.token))
            assertFalse(envelope.contains(row.proof!!.signature)); assertTrue(runCatching { stale.save(f.owner, row.copy(state = "READY")) }.isFailure)
            assertEquals(row, KeystoreP2pKycStore(context, name, alias).load(f.owner))
            val other = "0x" + "1".repeat(40); prefs.edit().putString(other, envelope).commit()
            assertTrue(runCatching { KeystoreP2pKycStore(context, name, alias).load(other) }.isFailure)
            val corrupt = envelope.replace("\"ciphertext\":\"", "\"ciphertext\":\"AAAA")
            prefs.edit().putString(f.owner, corrupt).commit()
            val restarted = KeystoreP2pKycStore(context, name, alias)
            assertTrue(runCatching { restarted.load(f.owner) }.isFailure)
            assertTrue(runCatching { restarted.save(f.owner, row) }.isFailure)
            assertEquals(corrupt, prefs.getString(f.owner, null))
        } finally {
            prefs.edit().clear().commit(); context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); if (containsAlias(alias)) deleteEntry(alias) }
        }
    }
    @Test fun compiledHttpsKycCapabilitiesAreReadOnlyAndMatchPassportMainnet() = runBlocking {
        if (InstrumentationRegistry.getArguments().getString("p2pReadOnly") != "true") return@runBlocking
        val caps = HttpP2pKycGateway(org.astianx.midori.one.BuildConfig.FIAT_BACKEND_URL).capabilities()
        assertTrue(caps.enabled); assertTrue(caps.countries.contains("CO")); assertFalse(caps.countries.contains("KE")); assertFalse(caps.countries.contains("MX"))
        HttpP2pKycGateway(org.astianx.midori.one.BuildConfig.FIAT_BACKEND_URL).verified("0x" + "1".repeat(40))
    }
}
