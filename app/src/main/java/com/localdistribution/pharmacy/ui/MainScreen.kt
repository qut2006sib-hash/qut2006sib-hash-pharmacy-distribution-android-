@file:OptIn(ExperimentalMaterial3Api::class)

package com.localdistribution.pharmacy.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.localdistribution.pharmacy.core.Inspector
import kotlinx.coroutines.launch

/** Every place the app can show. Tabs are roots; the rest are pushed on top. */
sealed interface Screen {
    data object Dashboard : Screen
    data object Pharmacies : Screen
    data object Orders : Screen
    data object Products : Screen
    data object More : Screen
    data class PharmacyDetail(val id: Long) : Screen
    data class PharmacyEdit(val id: Long?) : Screen
    data class ProductEdit(val id: Long?) : Screen
    data class ContactEdit(val id: Long?, val pharmacyId: Long?) : Screen
    data object Contacts : Screen
    data class OrderDetail(val id: Long) : Screen
    data class OrderEdit(val id: Long?, val pharmacyId: Long?) : Screen
    data object Audit : Screen
    data class AuditDetail(val id: Long) : Screen
    data object Backups : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Dashboard)
    val current: Screen get() = stack.last()
    fun push(screen: Screen) { stack.add(screen) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(screen: Screen) { stack.clear(); stack.add(screen) }
    /** Replace the current screen (e.g. editor → saved record). */
    fun replace(screen: Screen) { stack[stack.lastIndex] = screen }
}

private val Green = Color(0xFF0B6E4F)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF6FD3AE), secondary = Color(0xFF9CCFBE))
    else lightColorScheme(primary = Green, secondary = Color(0xFF3F6F60), secondaryContainer = Color(0xFFD4EDE3))
    MaterialTheme(colorScheme = scheme) {
        // The app is Arabic-first: right-to-left regardless of the phone's language, like the desktop.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Screen.Dashboard, "الرئيسية", Icons.Default.Home),
    Tab(Screen.Pharmacies, "الصيدليات", Icons.Default.Person),
    Tab(Screen.Orders, "الطلبيات", Icons.Default.ShoppingCart),
    Tab(Screen.Products, "المنتجات", Icons.AutoMirrored.Filled.List),
    Tab(Screen.More, "المزيد", Icons.Default.Menu),
)

private fun title(screen: Screen): String = when (screen) {
    Screen.Dashboard -> "إدارة توزيع المنتجات للصيدليات"
    Screen.Pharmacies -> "الصيدليات"
    Screen.Orders -> "الطلبيات"
    Screen.Products -> "المنتجات"
    Screen.More -> "المزيد"
    is Screen.PharmacyDetail -> "ملف الصيدلية"
    is Screen.PharmacyEdit -> "بيانات الصيدلية"
    is Screen.ProductEdit -> "بيانات المنتج"
    is Screen.ContactEdit -> if (screen.id == null) "تسجيل تواصل" else "تصحيح سجل التواصل"
    Screen.Contacts -> "سجل التواصل"
    is Screen.OrderDetail -> "بيان الطلبية"
    is Screen.OrderEdit -> "طلبية — حفظ كمسودة"
    Screen.Audit -> "سجل التعديلات"
    is Screen.AuditDetail -> "تفاصيل التعديل"
    Screen.Backups -> "البيانات والنسخ الاحتياطي"
}

@Composable
fun AppRoot(state: AppState) {
    val nav = remember { Nav() }
    when {
        state.starting && state.service == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.startupError != null -> StartupFailure(state)
        else -> Main(state, nav)
    }
    state.dialog?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text(title) }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { state.dialog = null }) { Text("حسنًا") } },
        )
    }
}

@Composable
private fun Main(state: AppState, nav: Nav) {
    val screen = nav.current
    val root = nav.stack.size == 1
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    BackHandler(enabled = !root) { nav.pop() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title(screen), maxLines = 1) },
                navigationIcon = {
                    if (!root) {
                        // Goes through the back dispatcher so editors can ask before discarding changes.
                        IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (root) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = screen == tab.screen, onClick = { nav.tab(tab.screen) },
                            icon = { Icon(tab.icon, null) }, label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.Dashboard -> DashboardScreen(state, nav)
                Screen.Pharmacies -> PharmaciesScreen(state, nav)
                Screen.Orders -> OrdersScreen(state, nav)
                Screen.Products -> ProductsScreen(state, nav)
                Screen.More -> MoreScreen(state, nav)
                is Screen.PharmacyDetail -> PharmacyDetailScreen(state, nav, screen.id)
                is Screen.PharmacyEdit -> PharmacyEditScreen(state, nav, screen.id)
                is Screen.ProductEdit -> ProductEditScreen(state, nav, screen.id)
                is Screen.ContactEdit -> ContactEditScreen(state, nav, screen.id, screen.pharmacyId)
                Screen.Contacts -> ContactsScreen(state, nav)
                is Screen.OrderDetail -> OrderDetailScreen(state, nav, screen.id)
                is Screen.OrderEdit -> OrderEditScreen(state, nav, screen.id, screen.pharmacyId)
                Screen.Audit -> AuditScreen(state, nav)
                is Screen.AuditDetail -> AuditDetailScreen(state, screen.id)
                Screen.Backups -> BackupsScreen(state)
            }
        }
    }
}

/**
 * Startup refused to open the data (damaged or incompatible file). Nothing
 * was deleted or replaced; offer to restore one of the automatic copies.
 */
@Composable
private fun StartupFailure(state: AppState) {
    val scope = rememberCoroutineScope()
    val backups = remember(state.revision) {
        state.root.resolve("backups").listFiles { f -> f.name.endsWith(".sqlite3") && !f.name.startsWith(".") }
            ?.sortedByDescending { it.name }.orEmpty()
    }
    val pick = rememberOpenDocument { uri ->
        scope.launch {
            state.task { state.importToScratch(uri) }?.let { file -> restoreAndRestart(state, file) }
        }
    }
    run {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("تعذّر فتح البيانات", style = MaterialTheme.typography.headlineSmall)
            Text("لم يتم حذف قاعدة البيانات أو إنشاء قاعدة بديلة فوقها.\n" + state.startupError.orEmpty())
            Button(onClick = { scope.launch { state.start() } }) { Text("إعادة المحاولة") }
            OutlinedButton(onClick = { pick() }) { Text("استعادة نسخة سليمة من ملف") }
            if (backups.isNotEmpty()) {
                SectionTitle("النسخ الاحتياطية على الهاتف")
                Hint("اختر نسخة للاستعادة. ستُحفظ البيانات الحالية التالفة في مجلد منفصل قبل الاستعادة.")
                backups.take(30).forEach { file ->
                    InfoCard(onClick = { scope.launch { restoreAndRestart(state, file) } }) {
                        Text(ltr(file.name), style = MaterialTheme.typography.bodyMedium)
                        Hint("${maxOf(1, file.length() / 1024)} كيلوبايت")
                    }
                }
            }
        }
    }
}

private suspend fun restoreAndRestart(state: AppState, file: java.io.File) {
    var restored = false
    val preserved = state.task("تعذّرت الاستعادة") {
        Inspector.inspect(file, state.factory)
        val kept = com.localdistribution.pharmacy.core.AppDatabase.restoreWithoutOpening(state.root, state.factory, file)
        restored = true
        kept?.path ?: "—"
    }
    if (restored) {
        state.start()
        if (state.startupError == null) {
            state.dialog = "استُعيدت النسخة" to "حُفظت الملفات السابقة في:\n$preserved"
        }
    }
}

@Composable
fun FullWidthButton(text: String, onClick: () -> Unit, enabled: Boolean = true, outlined: Boolean = false) {
    if (outlined) OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
    else Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}
