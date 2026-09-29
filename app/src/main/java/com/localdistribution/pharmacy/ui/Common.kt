package com.localdistribution.pharmacy.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.localdistribution.pharmacy.core.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Load data off the main thread; reloads whenever the app's revision or `key` changes. */
@Composable
fun <T> rememberData(state: AppState, key: Any? = null, fetch: (Service) -> T): Result<T>? {
    val value by produceState<Result<T>?>(null, state.revision, key, state.service) {
        val svc = state.service
        value = if (svc == null) null else withContext(Dispatchers.IO) { runCatching { fetch(svc) } }
    }
    return value
}

/** Shows a spinner while loading and a message if the record is gone. */
@Composable
fun <T> Loaded(result: Result<T>?, content: @Composable (T) -> Unit) {
    when {
        result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        result.isFailure -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(result.exceptionOrNull()?.message ?: "تعذّر تحميل البيانات.")
        }
        else -> content(result.getOrThrow())
    }
}

@Composable
fun FormColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    ltr: Boolean = false, number: Boolean = false, phone: Boolean = false, lines: Int = 1, placeholder: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = lines == 1, minLines = lines,
        textStyle = if (ltr || number || phone) MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
        else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = when {
            number -> KeyboardType.Decimal
            phone -> KeyboardType.Phone
            else -> KeyboardType.Text
        }),
    )
}

@Composable
fun <T> Dropdown(
    label: String, options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {}, readOnly = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            label = { Text(label) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
        )
        // The text field swallows taps, so a transparent layer opens the menu.
        Box(Modifier.matchParentSize().clickable(enabled = enabled) { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, optional: Boolean = true) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label) }, placeholder = { Text("YYYY-MM-DD" + if (optional) " — اختياري" else "") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        trailingIcon = {
            Row {
                if (optional && value.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "مسح التاريخ") }
                }
                IconButton(onClick = {
                    val d = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
                    DatePickerDialog(context, { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toString()) },
                        d.year, d.monthValue - 1, d.dayOfMonth).show()
                }) { Icon(Icons.Default.DateRange, "اختيار التاريخ") }
            }
        },
    )
}

private val MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Date and time of a contact, shown as "YYYY-MM-DD HH:MM" like the desktop form. */
@Composable
fun DateTimeField(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(label) }, placeholder = { Text("YYYY-MM-DD HH:MM") },
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
        trailingIcon = {
            IconButton(onClick = {
                val current = runCatching { LocalDateTime.parse(value.trim().replace(' ', 'T')) }.getOrElse { LocalDateTime.now() }
                DatePickerDialog(context, { _, y, m, d ->
                    TimePickerDialog(context, { _, h, min ->
                        onChange(LocalDateTime.of(y, m + 1, d, h, min).format(MINUTES))
                    }, current.hour, current.minute, true).show()
                }, current.year, current.monthValue - 1, current.dayOfMonth).show()
            }) { Icon(Icons.Default.DateRange, "اختيار التاريخ والوقت") }
        },
    )
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, warning: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = if (warning) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    else CardDefaults.cardColors()
    val m = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Card(m, colors = colors) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content) }
}

@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Box(Modifier.padding(end = 6.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = color)) {
            Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

/** LTR island for phone numbers, codes and dates inside Arabic text. */
fun ltr(value: String?): String = if (value.isNullOrEmpty()) "" else "⁦$value⁩"

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "نعم", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لا") } },
    )
}

/** Case-insensitive search across every field of a record, as on desktop. */
fun matches(query: String, vararg values: Any?): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return values.any { (it?.toString() ?: "").lowercase().contains(q) }
}
