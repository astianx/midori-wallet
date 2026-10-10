package org.astianx.midori.one.wallet.p2p

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.astianx.midori.one.MidoriTheme
import org.astianx.midori.one.R
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full native flows with in-memory accounts. No real wallet, bank, server or signing. */
@RunWith(AndroidJUnit4::class)
class P2pFlowDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun englishBuy() = flow("en", FiatOperation.BUY)
    @Test fun spanishBuy() = flow("es", FiatOperation.BUY)
    @Test fun portugueseBuy() = flow("pt", FiatOperation.BUY)
    @Test fun englishWithdraw() = flow("en", FiatOperation.SELL)
    @Test fun spanishWithdraw() = flow("es", FiatOperation.SELL)
    @Test fun portugueseWithdraw() = flow("pt", FiatOperation.SELL)
    @Test fun zeroLimitShowsAnAccountBlockWithoutCreatingAnIdentity() = flow("en", FiatOperation.BUY, true)
    private fun flow(language: String, operation: FiatOperation, zeroLimit: Boolean = false) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = target.createConfigurationContext(config)
        val fixture = P2pOrderFixture().apply { if (zeroLimit) buyLimit = "0" }
        val models = ViewModelStore()
        lateinit var vm: P2pOrderViewModel
        compose.runOnUiThread { vm = fixture.viewModel(operation); models.put("orders", vm) }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                    MidoriTheme {
                        val state by vm.state.collectAsState()
                        P2pOrderContent(state, {}, vm::operation, vm::country, vm::amount, vm::destination,
                            vm::quote, vm::reload, vm::action, vm::refresh, vm::instructions, vm::recover,
                            vm::submit, vm::dismiss)
                    }
                }
            }
            compose.onNodeWithTag("p2po-country").performClick()
            compose.onNodeWithText("${Locale("", "CO").getDisplayCountry(Locale.forLanguageTag(language))} · COP").performClick()
            compose.onNodeWithTag("p2po-account").assertExists()
            compose.onNodeWithText(context.getString(R.string.p2po_account_limit, if (zeroLimit) "0" else "100")).assertExists()
            compose.onNodeWithTag("p2po-amount").performTextInput(if (language == "en") "1" else "1,0")
            if (zeroLimit) {
                compose.onNodeWithText(context.getString(R.string.p2po_review)).performScrollTo().assertIsNotEnabled()
                compose.onNodeWithText(context.getString(R.string.p2po_account_disabled)).assertExists()
                assertEquals(0, fixture.identities); assertEquals(0, fixture.emissions)
                return
            }
            if (operation == FiatOperation.SELL) compose.onNodeWithTag("p2po-destination-alias").performTextInput("3001234567")
            fun click(resource: Int) = compose.onNodeWithText(context.getString(resource)).performScrollTo().performClick()
            fun refreshOrder() = compose.onAllNodesWithText(context.getString(R.string.p2po_refresh)).onLast().performScrollTo().performClick()
            click(R.string.p2po_review)
            compose.onNodeWithTag("p2po-review").assertExists()
            click(R.string.p2po_confirm); compose.waitForIdle(); assertEquals(1, fixture.emissions)
            refreshOrder()
            compose.onNodeWithText(context.getString(R.string.p2po_accepted)).performScrollTo().assertIsDisplayed()
            if (operation == FiatOperation.BUY) {
                click(R.string.p2po_payment_details)
                compose.onNodeWithText("Fixture bank · 123456").performScrollTo().assertIsDisplayed()
                click(R.string.p2po_paid)
                compose.onNodeWithText(context.getString(R.string.p2po_confirm)).performScrollTo().assertIsNotEnabled()
                compose.onNode(isToggleable()).performScrollTo().performClick()
                click(R.string.p2po_confirm)
                assertEquals(2, fixture.emissions)
            } else {
                click(R.string.p2po_approve); click(R.string.p2po_confirm)
                refreshOrder()
                click(R.string.p2po_handoff)
                compose.onNodeWithText("3001234567").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.p2po_total_usdc, "1")).assertExists()
                click(R.string.p2po_confirm); assertEquals(3, fixture.emissions)
            }
            compose.waitForIdle()
            fixture.order = fixture.order!!.copy(status = "completed")
            refreshOrder()
            compose.onNodeWithText(context.getString(R.string.p2po_completed)).performScrollTo().assertIsDisplayed()
            assertEquals(1, fixture.identities)
            assertFalse(fixture.store.rows.last().toString().contains("3001234567"))
            val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(context.cacheDir, "p2p-orders-$language-${operation.name}.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { compose.runOnUiThread { models.clear() } }
    }
}
