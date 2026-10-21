package org.astianx.midori.one.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import org.astianx.midori.one.PrimaryButton
import org.astianx.midori.one.R
import org.astianx.midori.one.ScreenHeader

@Composable
internal fun CountryChoice(selected: String, enabled: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val countries = remember(locale) { walletCountries(locale) }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column(modifier) {
        Text(stringResource(R.string.wallet_country_label), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ query = ""; expanded = true }, enabled = enabled,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("wallet-country")) {
                Text(countries.firstOrNull { it.first == selected }?.second ?: stringResource(R.string.fiat_select_country),
                    modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.wallet_country_warning), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (expanded) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(stringResource(R.string.fiat_select_country)) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth().testTag("wallet-country-search"))
                Spacer(Modifier.height(12.dp))
                val matches = countries.filter { (code, name) ->
                    query.isBlank() || name.contains(query.trim(), ignoreCase = true) || code.equals(query.trim(), ignoreCase = true)
                }
                LazyColumn(Modifier.heightIn(max = 320.dp).testTag("wallet-country-list")) {
                    items(matches, key = { it.first }) { (code, name) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(selected == code, role = Role.RadioButton,
                                onClick = { expanded = false; onSelect(code) })
                            .testTag("wallet-country-$code"), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == code, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                    if (matches.isEmpty()) item { Text(stringResource(R.string.no_results), Modifier.padding(vertical = 20.dp)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton({ expanded = false }) { Text(stringResource(R.string.cancel)) } }
    )
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
