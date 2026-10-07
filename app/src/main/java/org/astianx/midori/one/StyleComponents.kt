package org.astianx.midori.one

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun StylePage(title: Int, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(stringResource(title), back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun StyleToggle(label: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    val text = stringResource(label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(value, onChange, Modifier.semantics { contentDescription = text })
    }
}

@Composable
internal fun StyleSlider(label: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var dragging by remember(value) { mutableStateOf(value.toFloat()) }
    val text = stringResource(label)
    Column {
        Row { Text(text, Modifier.weight(1f)); Text(dragging.roundToInt().toString(), color = MaterialTheme.colorScheme.primary) }
        Slider(dragging, { dragging = it }, valueRange = range.first.toFloat()..range.last.toFloat(),
            onValueChangeFinished = { onChange(dragging.roundToInt()) },
            modifier = Modifier.semantics { contentDescription = text })
    }
}

@Composable
internal fun <T> StyleChoices(label: Int, values: List<T>, selected: T, text: @Composable (T) -> String, onChange: (T) -> Unit) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value -> FilterChip(selected == value, { onChange(value) }, label = { Text(text(value)) }) }
        }
    }
}
