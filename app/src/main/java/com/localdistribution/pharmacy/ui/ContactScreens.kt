package com.localdistribution.pharmacy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdistribution.pharmacy.core.Clock
import com.localdistribution.pharmacy.core.Contact
import com.localdistribution.pharmacy.core.ContactInput
import com.localdistribution.pharmacy.core.Labels
import com.localdistribution.pharmacy.core.Pharmacy
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen(state: AppState, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val data = rememberData(state) { s -> s.contacts() to s.pharmacies().associate { it.id to it.name } }
    Box(Modifier.fillMaxSize()) {
        Column {
            SearchBar(query, { query = it })
            Loaded(data) { (contacts, names) ->
                val rows = contacts.filter { matches(query, names[it.pharmacyId], it.note, it.occurredAt, Labels.KINDS[it.kind]) }
                if (rows.isEmpty()) Text("لا توجد سجلات تواصل.", Modifier.padding(24.dp))
                LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.id }) { c ->
                        InfoCard(onClick = { nav.push(Screen.ContactEdit(c.id, c.pharmacyId)) }) {
                            Text(names[c.pharmacyId] ?: "", fontWeight = FontWeight.Bold)
                            Text("${ltr(c.occurredAt.replace('T', ' '))} — ${Labels.KINDS[c.kind] ?: c.kind}")
                            Text(c.note, maxLines = 3)
                            c.nextFollowUpOn?.let { Hint("المتابعة المسجلة: ${ltr(it)}") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { nav.push(Screen.ContactEdit(null, null)) }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Icon(Icons.Default.Add, "تسجيل تواصل")
        }
    }
}

@Composable
fun ContactEditScreen(state: AppState, nav: Nav, id: Long?, pharmacyId: Long?) {
    val data = rememberData(state, id) { s -> id?.let { s.contact(it) } to s.pharmacies() }
    Loaded(data) { (contact, pharmacies) ->
        if (pharmacies.isEmpty()) {
            Text("أضف صيدلية أولًا.", Modifier.padding(24.dp))
        } else {
            ContactForm(state, nav, contact, pharmacies, pharmacyId)
        }
    }
}

@Composable
private fun ContactForm(state: AppState, nav: Nav, c: Contact?, pharmacies: List<Pharmacy>, pharmacyId: Long?) {
    val scope = rememberCoroutineScope()
    val firstPharmacy = c?.pharmacyId ?: pharmacyId ?: pharmacies.first().id
    fun followUpOf(id: Long) = pharmacies.firstOrNull { it.id == id }?.followUpOn ?: ""
    val initial = remember(c) {
        ContactInput(firstPharmacy, (c?.occurredAt ?: Clock.nowLocalMinutes()).replace('T', ' '), c?.kind ?: "visit",
            c?.note ?: "", c?.nextFollowUpOn ?: if (c == null) followUpOf(firstPharmacy) else "")
    }
    var v by remember(c) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    UnsavedChangesGuard(dirty = v != initial, nav = nav)
    FormColumn {
        Dropdown("الصيدلية *", pharmacies.map { it.id to (it.name + if (!it.active) " — مؤرشفة" else "") }, v.pharmacyId, { picked ->
            // A new contact starts from the pharmacy's current plan, as on desktop.
            v = v.copy(pharmacyId = picked, nextFollowUpOn = followUpOf(picked))
        }, enabled = c == null)
        Dropdown("نوع التواصل", Labels.KINDS.toList(), v.kind, { v = v.copy(kind = it) })
        DateTimeField("تاريخ ووقت التواصل", v.occurredAt, { v = v.copy(occurredAt = it) })
        Field("النتيجة والملاحظات *", v.note, { v = v.copy(note = it) }, lines = 5)
        DateField("المتابعة القادمة", v.nextFollowUpOn, { v = v.copy(nextFollowUpOn = it) })
        Hint(if (c == null) "تسجيل تواصل جديد يحدّث موعد متابعة الصيدلية؛ ترك الموعد فارغًا يلغي الموعد السابق."
             else "هذا تصحيح لسجل سابق؛ موعد المتابعة الحالي للصيدلية لن يتغيّر.")
        FullWidthButton("حفظ", enabled = !saving, onClick = {
            saving = true
            scope.launch {
                val saved = state.write { it.saveContact(v, c?.id) }
                saving = false
                if (saved != null) nav.pop()
            }
        })
    }
}
