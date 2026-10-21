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
import org.astianx.midori.one.wallet.CountryChoice
import org.astianx.midori.one.wallet.fiat.FiatOperation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory fixtures only: no wallet, signer or server. */
@RunWith(AndroidJUnit4::class)
class P2pCountryDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun englishCountrySelectionAndLockedMarket() = run("en")
    @Test fun spanishCountrySelectionAndLockedMarket() = run("es")
    @Test fun portugueseCountrySelectionAndLockedMarket() = run("pt")

    private fun run(language: String) {
        val locale = Locale.forLanguageTag(language)
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(target.resources.configuration).apply { setLocale(locale) }
        val context = target.createConfigurationContext(config)
        val name = Locale("", "CO").getDisplayCountry(locale)
        val models = ViewModelStore()
        lateinit var vm: P2pOrderViewModel
        compose.runOnUiThread { vm = P2pOrderFixture().viewModel(FiatOperation.BUY, "CO"); models.put("orders", vm) }
        try {
            var selected by mutableStateOf("")
            var showMarket by mutableStateOf(false)
            compose.setContent {
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config) {
                    MidoriTheme {
                        if (!showMarket) CountryChoice(selected, true, { selected = it })
                        else {
                            val state by vm.state.collectAsState()
                            P2pOrderContent(state, {}, vm::operation, vm::country, vm::amount, vm::destination,
                                vm::quote, vm::reload, vm::action, vm::refresh, vm::instructions, vm::recover,
                                vm::submit, vm::dismiss)
                        }
                    }
                }
            }
            compose.onNodeWithText(context.getString(R.string.wallet_country_warning)).assertExists()
            compose.onNodeWithTag("wallet-country").performClick()
            compose.onNodeWithTag("wallet-country-search").performTextInput("CO")
            compose.onNodeWithTag("wallet-country-list").performScrollToNode(hasTestTag("wallet-country-CO"))
            compose.onNodeWithTag("wallet-country-CO").assertIsDisplayed().performClick()
            compose.onNodeWithTag("wallet-country").assertTextContains(name)
            compose.runOnIdle { showMarket = true }
            compose.onNodeWithTag("p2po-country").assertTextEquals(context.getString(R.string.wallet_country_locked, "$name · COP"))
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("p2po-account").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("p2po-account").assertExists()
            compose.onNodeWithTag("p2po-country-unavailable").assertDoesNotExist()
            compose.onNodeWithTag("p2po-country").assertHasNoClickAction()
        } finally {
            compose.runOnUiThread { models.clear() }
        }
    }
}
