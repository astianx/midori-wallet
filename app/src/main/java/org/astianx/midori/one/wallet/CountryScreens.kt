package org.astianx.midori.one.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.ScreenHeader

@Composable
internal fun CountryChoice(selected: String, enabled: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val countries = remember(locale) { walletCountries(locale) }
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(stringResource(R.string.wallet_country_label), style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("wallet-country")) {
                Text(countries.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.fiat_select_country),
                    modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 360.dp)) {
                countries.forEach { (code, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(code) },
                        modifier = Modifier.testTag("wallet-country-$code"))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.wallet_country_warning), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun WalletCountryGate(vm: WalletViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.wallet_country_gate_title), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.wallet_country_gate_body), style = MaterialTheme.typography.bodyLarge)
            CountryChoice(state.pendingCountry, !state.isLoading, vm::selectCountry)
            PrimaryButton(stringResource(R.string.wallet_country_confirm), walletCountryValid(state.pendingCountry) && !state.isLoading,
                vm::confirmCountry)
        }
    }
}
