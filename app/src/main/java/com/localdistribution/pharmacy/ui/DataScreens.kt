package com.localdistribution.pharmacy.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.localdistribution.pharmacy.core.Clock
import com.localdistribution.pharmacy.core.Exports
import com.localdistribution.pharmacy.core.InspectResult
import com.localdistribution.pharmacy.core.Inspector
import com.localdistribution.pharmacy.core.Json
import com.localdistribution.pharmacy.core.Labels
import com.localdistribution.pharmacy.core.Validate
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DashboardScreen(state: AppState, nav: Nav) {
    val data = rememberData(state) { it.dashboard() }
    Loaded(data) { d ->
        FormColumn {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("الصيدليات النشطة", d.pharmacies, Modifier.weight(1f)) { nav.tab(Screen.Pharmacies) }
                Stat("المتعاقدة", d.contracted, Modifier.weight(1f)) { nav.tab(Screen.Pharmacies) }
                Stat("طلبيات قيد المتابعة", d.pending, Modifier.weight(1f)) { nav.tab(Screen.Orders) }
            }
            BackupBanner(state) { nav.push(Screen.Backups) }
            SectionTitle("متابعات اليوم والمتابعات المتأخرة")
            if (d.followups.isEmpty()) Hint("لا توجد متابعات مستحقة.")
            d.followups.forEach { p ->
                val overdue = (p.followUpOn ?: "") < Clock.today()
                InfoCard(onClick = { nav.push(Screen.PharmacyDetail(p.id)) }, warning = overdue) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    Text(listOf(p.area, ltr(p.phone)).filter { it.isNotBlank() }.joinToString(" · "))
                    Text("الموعد: ${ltr(p.followUpOn)}" + if (overdue) " — متأخرة" else "")
                }
            }
            SectionTitle("قيمة الطلبيات حسب العملة والحالة")
            Hint("لا تمثل المقبوضات أو الأرباح.")
            if (d.totals.isEmpty()) Hint("لا توجد طلبيات.")
            d.totals.forEach { t ->
                InfoCard {
                    Text("${t.currency} — ${Labels.ORDER_STATES[t.status] ?: t.status}", fontWeight = FontWeight.Bold)
                    Text("العدد: ${t.count} · القيمة: ${ltr(Validate.moneyText(t.totalMinor))}")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Long, modifier: Modifier, onClick: () -> Unit) {
    InfoCard(modifier, onClick = onClick) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BackupBanner(state: AppState, onClick: () -> Unit) {
    val backups = state.db?.backups ?: return
    // Read on every recomposition; revision changes after each save.
    state.revision
    val error = backups.lastError
    if (error.isNotEmpty()) {
        InfoCard(onClick = onClick, warning = true) {
            Text("البيانات محفوظة، لكن آخر محاولة للنسخ الاحتياطي فشلت:", fontWeight = FontWeight.Bold)
            Text(error)
        }
    } else {
        InfoCard(onClick = onClick) {
            Text("آخر نسخة احتياطية ناجحة: " + (backups.lastSuccess?.name?.let(::ltr) ?: "غير متاحة"), style = MaterialTheme.typography.bodySmall)
            Hint("احفظ نسخة خارج الهاتف دوريًا من «البيانات والنسخ الاحتياطي».")
        }
    }
}

@Composable
fun MoreScreen(state: AppState, nav: Nav) {
    FormColumn {
        InfoCard(onClick = { nav.push(Screen.Contacts) }) {
            Text("سجل التواصل", fontWeight = FontWeight.Bold); Hint("كل الزيارات والاتصالات وتصحيحها")
        }
        InfoCard(onClick = { nav.push(Screen.Backups) }) {
            Text("البيانات والنسخ الاحتياطي", fontWeight = FontWeight.Bold); Hint("نسخ، استعادة، تصدير، ونقل البيانات من وإلى نسخة الحاسوب")
        }
        InfoCard(onClick = { nav.push(Screen.Audit) }) {
            Text("سجل التعديلات", fontWeight = FontWeight.Bold); Hint("كل إضافة وتعديل مع القيم قبل وبعد")
        }
        Hint("الإصدار ${ltr(state.version)} — يعمل دون إنترنت؛ البيانات محفوظة على هذا الهاتف فقط.")
    }
}

@Composable
fun AuditScreen(state: AppState, nav: Nav) {
    val data = rememberData(state) { it.auditEvents(1000) }
    Loaded(data) { events ->
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Hint("آخر 1000 تعديل في هذه الشاشة. التصدير والنسخ الاحتياطي يشملان كامل التاريخ.") }
            items(events, key = { it.id }) { e ->
                InfoCard(onClick = { nav.push(Screen.AuditDetail(e.id)) }) {
                    Text("${Labels.AUDIT_ACTIONS[e.action] ?: e.action} — ${Labels.AUDIT_ENTITIES[e.entity] ?: e.entity}" +
                        (e.entityId?.let { " #$it" } ?: ""), fontWeight = FontWeight.Bold)
                    Hint("الوقت UTC: ${ltr(e.occurredAt)}")
                }
            }
        }
    }
}

@Composable
fun AuditDetailScreen(state: AppState, id: Long) {
    val data = rememberData(state, id) { it.auditEvent(id) }
    Loaded(data) { e ->
        FormColumn {
            Text("${Labels.AUDIT_ACTIONS[e.action] ?: e.action} — ${ltr(e.occurredAt)}", fontWeight = FontWeight.Bold)
            SectionTitle("قبل التعديل")
            JsonBlock(e.beforeJson)
            SectionTitle("بعد التعديل")
            JsonBlock(e.afterJson)
        }
    }
}

@Composable
private fun JsonBlock(text: String) {
    val pretty = remember(text) { runCatching { Json.pretty(Json.parse(text)) }.getOrDefault(text) }
    InfoCard { Text(pretty, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)) }
}

@Composable
fun rememberOpenDocument(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onPicked(uri) }
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
fun rememberCreateDocument(mime: String, onPicked: (Uri) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri -> if (uri != null) onPicked(uri) }
    return { name -> launcher.launch(name) }
}

/** A restore waiting for the user's confirmation, with what the file contains. */
private data class PendingRestore(val file: File, val info: InspectResult)

@Composable
fun BackupsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val db = state.db ?: return
    var pending by remember { mutableStateOf<PendingRestore?>(null) }
    var selected by remember { mutableStateOf<File?>(null) }
    val backups = rememberData(state) { db.backups.list() }

    fun prepareRestore(file: File) {
        scope.launch {
            state.task("تعذّرت الاستعادة") { Inspector.inspect(file, state.factory) }?.let { pending = PendingRestore(file, it) }
        }
    }

    val saveBackup = rememberCreateDocument("application/octet-stream") { uri ->
        scope.launch {
            val ok = state.task("تعذّر حفظ النسخة") {
                val temp = state.scratch("external.sqlite3")
                db.backup("external", temp)
                state.exportTo(temp, uri)
                state.cleanScratch()
            }
            if (ok != null) state.dialog = "اكتملت النسخة" to "حُفظت النسخة وفُحصت سلامتها، وتم التحقق من الملف بعد كتابته."
        }
    }
    val exportZip = rememberCreateDocument("application/zip") { uri ->
        scope.launch {
            val ok = state.task("تعذّر التصدير") {
                val temp = state.scratch("export.zip")
                Exports.exportAll(db, temp, state.version)
                state.exportTo(temp, uri)
                state.cleanScratch()
            }
            if (ok != null) state.dialog = "اكتمل التصدير" to "يشمل الملف جميع السجلات والمؤرشف وسجل التعديلات، بصيغتي JSON وCSV."
        }
    }
    val restoreFile = rememberOpenDocument { uri ->
        scope.launch { state.task("تعذّر قراءة الملف") { state.importToScratch(uri) }?.let { prepareRestore(it) } }
    }

    Loaded(backups) { files ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BackupBanner(state) {}
                    Hint("تُحفظ نسخة بعد كل تعديل ناجح وعند فتح التطبيق. تُحذف تلقائيًا فقط النسخ التلقائية الأقدم من آخر ${com.localdistribution.pharmacy.core.BackupManager.AUTO_KEEP} نسخة من كل نوع؛ لا تُحذف النسخ اليدوية.")
                    Hint("النسخ داخل الهاتف لا تحمي من ضياعه أو حذف التطبيق: احفظ نسخة إلى ملف دوريًا (Google Drive أو بطاقة ذاكرة أو أرسلها لنفسك).")
                    Hint("استعادة نسخة تعيد البيانات إلى وقتها، مع الاحتفاظ بالبيانات الحالية في نسخة منفصلة أولًا. يمكن استعادة نسخ الحاسوب (.sqlite3) هنا، ونسخ الهاتف على الحاسوب.")
                    FullWidthButton("نسخة احتياطية الآن", onClick = {
                        scope.launch { if (state.task { db.backup("manual") } != null) state.dialog = "تمت النسخة" to "تم إنشاء نسخة احتياطية وفحص سلامتها." }
                    })
                    FullWidthButton("حفظ نسخة إلى ملف…", outlined = true, onClick = { saveBackup("pharmacies_${Clock.stamp()}.sqlite3") })
                    FullWidthButton("استعادة من ملف (من الهاتف أو الحاسوب)…", outlined = true, onClick = { restoreFile() })
                    FullWidthButton("تصدير كل البيانات JSON + CSV…", outlined = true, onClick = { exportZip("pharmacy_data_${Clock.stamp()}.zip") })
                    FullWidthButton("فحص قاعدة البيانات الحالية", outlined = true, onClick = {
                        scope.launch {
                            state.task { db.inspectCurrent() }?.let {
                                state.dialog = "البيانات سليمة" to "نجح فحص قاعدة البيانات والعلاقات وإجماليات الطلبيات.\nالصيدليات: ${it.pharmacies}\nالطلبيات: ${it.orders}"
                            }
                        }
                    })
                    SectionTitle("النسخ على الهاتف (${files.size})")
                }
            }
            items(files, key = { it.name }) { file ->
                InfoCard(onClick = { selected = file }) {
                    Text(ltr(file.name), style = MaterialTheme.typography.bodyMedium)
                    Hint("${maxOf(1, file.length() / 1024)} كيلوبايت")
                }
            }
        }
    }

    selected?.let { file ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("النسخة المحددة") },
            text = { Text(ltr(file.name)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        selected = null
                        scope.launch {
                            state.task { Inspector.inspect(file, state.factory) }?.let {
                                state.dialog = "النسخة سليمة" to "الصيدليات: ${it.pharmacies}\nالطلبيات: ${it.orders}"
                            }
                        }
                    }) { Text("فحص") }
                    TextButton(onClick = { selected = null; prepareRestore(file) }) { Text("استعادة") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("إغلاق") } },
        )
    }

    pending?.let { p ->
        ConfirmDialog("استعادة البيانات",
            "النسخة تحتوي ${p.info.pharmacies} صيدلية و${p.info.orders} طلبية.\nستعود بيانات التطبيق إلى محتواها.\n" +
                "سنحتفظ بالبيانات الحالية في نسخة منفصلة قبل الاستعادة. هل تريد المتابعة؟",
            confirm = "استعادة",
            onConfirm = {
                scope.launch {
                    val preserved = state.task("تعذّرت الاستعادة") { db.restore(p.file)?.path ?: "—" }
                    if (preserved != null) {
                        state.cleanScratch()
                        state.dialog = "اكتملت الاستعادة" to "استُعيدت البيانات. حُفظت البيانات السابقة في:\n$preserved"
                    }
                }
            },
            onDismiss = { pending = null })
    }
}
