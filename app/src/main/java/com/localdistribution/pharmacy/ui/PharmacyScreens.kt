package com.localdistribution.pharmacy.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdistribution.pharmacy.core.Json
import com.localdistribution.pharmacy.core.Labels
import com.localdistribution.pharmacy.core.Pharmacy
import com.localdistribution.pharmacy.core.PharmacyInput
import com.localdistribution.pharmacy.core.Validate
import kotlinx.coroutines.launch

@Composable
fun SearchBar(query: String, onChange: (String) -> Unit, archived: Boolean? = null, onArchived: (Boolean) -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query, onValueChange = onChange, modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text("بحث…") }, leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        if (archived != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
                Switch(checked = archived, onCheckedChange = onArchived)
                Text("المؤرشف", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun PharmaciesScreen(state: AppState, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val data = rememberData(state) { it.pharmacies() }
    Box(Modifier.fillMaxSize()) {
        Column {
            SearchBar(query, { query = it }, archived) { archived = it }
            Loaded(data) { all ->
                val rows = all.filter { (archived || it.active) && matches(query, Json.dumps(it.record()), Labels.STAGES[it.stage], Labels.CONTRACTS[it.contractStatus]) }
                if (rows.isEmpty()) Text("لا توجد صيدليات مطابقة.", Modifier.padding(24.dp))
                LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.id }) { p -> PharmacyCard(p) { nav.push(Screen.PharmacyDetail(p.id)) } }
                }
            }
        }
        FloatingActionButton(onClick = { nav.push(Screen.PharmacyEdit(null)) }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Icon(Icons.Default.Add, "إضافة صيدلية")
        }
    }
}

@Composable
fun PharmacyCard(p: Pharmacy, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val line = listOf(p.area, ltr(p.phone)).filter { it.isNotBlank() }.joinToString(" · ")
        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium)
        Row {
            Badge(Labels.STAGES[p.stage] ?: p.stage)
            Badge(Labels.CONTRACTS[p.contractStatus] ?: p.contractStatus)
            if (!p.active) Badge("مؤرشف", MaterialTheme.colorScheme.errorContainer)
        }
        p.followUpOn?.let { Hint("المتابعة: ${ltr(it)}") }
    }
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "لا يوجد تطبيق لفتح هذا الإجراء.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** First number in a field like "0991586994 / 0312221957". */
private fun firstPhone(phone: String): String = phone.split('/', ',', '،').first().filter { it.isDigit() || it == '+' }

/** Syrian local numbers (09…, 0xx…) become international (963…) for WhatsApp links. */
private fun whatsappNumber(phone: String): String {
    val digits = firstPhone(phone).filter { it.isDigit() }
    return when {
        firstPhone(phone).startsWith("+") -> digits
        digits.startsWith("00") -> digits.drop(2)
        digits.startsWith("0") -> "963" + digits.drop(1)
        else -> digits
    }
}

@Composable
fun PharmacyDetailScreen(state: AppState, nav: Nav, id: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmArchive by remember { mutableStateOf(false) }
    val data = rememberData(state, id) { s -> Triple(s.pharmacy(id), s.contactsFor(id), s.orders().filter { it.pharmacyId == id }) }
    Loaded(data) { (p, contacts, orders) ->
        FormColumn {
            InfoCard {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (p.contactName.isNotBlank()) Text("مسؤول التواصل: ${p.contactName}")
                if (p.phone.isNotBlank()) Text("الهاتف: ${ltr(p.phone)}")
                if (p.area.isNotBlank()) Text("المنطقة: ${p.area}")
                if (p.address.isNotBlank()) Text("العنوان: ${p.address}")
                Row(Modifier.padding(top = 4.dp)) {
                    Badge(Labels.STAGES[p.stage] ?: p.stage)
                    Badge(Labels.CONTRACTS[p.contractStatus] ?: p.contractStatus)
                    if (!p.active) Badge("مؤرشفة", MaterialTheme.colorScheme.errorContainer)
                }
                Text("المتابعة القادمة: " + (p.followUpOn?.let(::ltr) ?: "غير محددة"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (p.phone.isNotBlank()) {
                    FilledTonalButton(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + firstPhone(p.phone)))) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Call, null); Text(" اتصال")
                    }
                    FilledTonalButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + whatsappNumber(p.phone)))) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.Send, null); Text(" واتساب")
                    }
                }
                if (p.address.isNotBlank()) {
                    FilledTonalButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(p.address)))) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Place, null); Text(" الخريطة")
                    }
                }
            }
            if (p.notes.isNotBlank()) InfoCard { Text("الملاحظات", fontWeight = FontWeight.Bold); Text(p.notes) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { nav.push(Screen.PharmacyEdit(id)) }, modifier = Modifier.weight(1f)) { Text("تعديل") }
                OutlinedButton(onClick = { nav.push(Screen.ContactEdit(null, id)) }, modifier = Modifier.weight(1f)) { Text("تسجيل تواصل") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { nav.push(Screen.OrderEdit(null, id)) }, enabled = p.active, modifier = Modifier.weight(1f)) { Text("طلبية جديدة") }
                OutlinedButton(onClick = { confirmArchive = true }, modifier = Modifier.weight(1f)) { Text(if (p.active) "أرشفة" else "إعادة تنشيط") }
            }
            SectionTitle("سجل التواصل")
            if (contacts.isEmpty()) Hint("لا توجد سجلات تواصل.")
            contacts.forEach { c ->
                InfoCard(onClick = { nav.push(Screen.ContactEdit(c.id, id)) }) {
                    Text("${ltr(c.occurredAt.replace('T', ' '))} — ${Labels.KINDS[c.kind] ?: c.kind}", fontWeight = FontWeight.Bold)
                    Text(c.note)
                    c.nextFollowUpOn?.let { Hint("المتابعة المسجلة: ${ltr(it)}") }
                }
            }
            SectionTitle("الطلبيات السابقة")
            if (orders.isEmpty()) Hint("لا توجد طلبيات.")
            orders.forEach { o ->
                InfoCard(onClick = { nav.push(Screen.OrderDetail(o.id)) }) {
                    Text("${ltr(o.number)} — ${ltr(o.orderDate)} — ${Labels.ORDER_STATES[o.status]}")
                    Text("سعر الطلبية: ${ltr(Validate.moneyText(o.orderPriceMinor))} ${o.currency}")
                }
            }
        }
        if (confirmArchive) {
            val action = if (p.active) "أرشفة" else "إعادة تنشيط"
            ConfirmDialog(action, "$action: ${p.name}؟\nسيُحتفظ بالبيانات وسجل التعاملات.", onConfirm = {
                scope.launch { state.write { it.archive("pharmacies", id, p.active) } }
            }, onDismiss = { confirmArchive = false })
        }
    }
}

@Composable
fun PharmacyEditScreen(state: AppState, nav: Nav, id: Long?) {
    val data = rememberData(state, id) { s -> id?.let { s.pharmacy(it) } }
    Loaded(data) { existing -> PharmacyForm(state, nav, id, existing) }
}

@Composable
private fun PharmacyForm(state: AppState, nav: Nav, id: Long?, p: Pharmacy?) {
    val scope = rememberCoroutineScope()
    val initial = remember(p) {
        PharmacyInput(p?.name ?: "", p?.phone ?: "", p?.contactName ?: "", p?.area ?: "", p?.address ?: "",
            p?.stage ?: "new", p?.contractStatus ?: "none", p?.followUpOn ?: "", p?.notes ?: "")
    }
    var v by remember(p) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    UnsavedChangesGuard(dirty = v != initial, nav = nav)
    FormColumn {
        Field("اسم الصيدلية *", v.name, { v = v.copy(name = it) })
        Field("مسؤول التواصل", v.contactName, { v = v.copy(contactName = it) })
        Field("الهاتف", v.phone, { v = v.copy(phone = it) }, phone = true)
        Field("المنطقة", v.area, { v = v.copy(area = it) })
        Field("العنوان", v.address, { v = v.copy(address = it) }, lines = 2)
        Dropdown("مرحلة التواصل", Labels.STAGES.toList(), v.stage, { v = v.copy(stage = it) })
        Dropdown("حالة التعاقد", Labels.CONTRACTS.toList(), v.contractStatus, { v = v.copy(contractStatus = it) })
        DateField("موعد المتابعة القادمة", v.followUpOn, { v = v.copy(followUpOn = it) })
        Field("الملاحظات", v.notes, { v = v.copy(notes = it) }, lines = 4)
        FullWidthButton("حفظ", enabled = !saving, onClick = {
            saving = true
            scope.launch {
                val saved = state.write { it.savePharmacy(v, id) }
                saving = false
                if (saved != null) {
                    if (id == null) nav.replace(Screen.PharmacyDetail(saved)) else nav.pop()
                }
            }
        })
    }
}

/** Back (system or toolbar) asks before throwing away edits, like the desktop dialogs. */
@Composable
fun UnsavedChangesGuard(dirty: Boolean, nav: Nav) {
    var asking by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { asking = true }
    if (asking) {
        ConfirmDialog("تغييرات غير محفوظة", "هل تريد إغلاق النافذة وتجاهل التغييرات؟", confirm = "تجاهل التغييرات",
            onConfirm = { nav.pop() }, onDismiss = { asking = false })
    }
}
