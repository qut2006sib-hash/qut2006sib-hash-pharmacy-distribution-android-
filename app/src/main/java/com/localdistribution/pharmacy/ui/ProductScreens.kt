package com.localdistribution.pharmacy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.localdistribution.pharmacy.core.Json
import com.localdistribution.pharmacy.core.Labels
import com.localdistribution.pharmacy.core.Product
import com.localdistribution.pharmacy.core.ProductInput
import com.localdistribution.pharmacy.core.Validate
import kotlinx.coroutines.launch

@Composable
fun ProductsScreen(state: AppState, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val data = rememberData(state) { it.products() }
    Box(Modifier.fillMaxSize()) {
        Column {
            SearchBar(query, { query = it }, archived) { archived = it }
            Loaded(data) { all ->
                val rows = all.filter { (archived || it.active) && matches(query, Json.dumps(it.record())) }
                if (rows.isEmpty()) Text("لا توجد منتجات مطابقة.", Modifier.padding(24.dp))
                LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.id }) { p ->
                        InfoCard(onClick = { nav.push(Screen.ProductEdit(p.id)) }) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(listOf(ltr(p.sku), p.brand, p.size).filter { it.isNotBlank() }.joinToString(" · "))
                            Row {
                                Badge("${ltr(Validate.moneyText(p.unitPriceMinor))} ${p.currency}")
                                if (!p.active) Badge("مؤرشف", MaterialTheme.colorScheme.errorContainer)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { nav.push(Screen.ProductEdit(null)) }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Icon(Icons.Default.Add, "إضافة منتج")
        }
    }
}

@Composable
fun ProductEditScreen(state: AppState, nav: Nav, id: Long?) {
    val data = rememberData(state, id) { s -> id?.let { s.product(it) } }
    Loaded(data) { existing -> ProductForm(state, nav, id, existing) }
}

@Composable
private fun ProductForm(state: AppState, nav: Nav, id: Long?, p: Product?) {
    val scope = rememberCoroutineScope()
    val initial = remember(p) {
        ProductInput(p?.name ?: "", p?.sku ?: "", p?.brand ?: "", p?.category ?: "", p?.size ?: "",
            p?.currency ?: "SYP", Validate.moneyText(p?.unitPriceMinor ?: 0), p?.notes ?: "")
    }
    var v by remember(p) { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    UnsavedChangesGuard(dirty = v != initial, nav = nav)
    FormColumn {
        if (p != null && !p.active) Badge("منتج مؤرشف", MaterialTheme.colorScheme.errorContainer)
        Field("اسم المنتج *", v.name, { v = v.copy(name = it) })
        Field("رمز المنتج — تلقائي إن تركته فارغًا", v.sku, { v = v.copy(sku = it) }, ltr = true)
        Field("العلامة التجارية", v.brand, { v = v.copy(brand = it) })
        Field("الفئة", v.category, { v = v.copy(category = it) })
        Field("الحجم / العبوة", v.size, { v = v.copy(size = it) })
        Dropdown("العملة", Labels.CURRENCIES.toList(), v.currency, { v = v.copy(currency = it) })
        Field("سعر الوحدة", v.price, { v = v.copy(price = it) }, number = true)
        Field("الملاحظات", v.notes, { v = v.copy(notes = it) }, lines = 3)
        Hint("تغيير سعر المنتج لا يغيّر أسعار الطلبيات المحفوظة سابقًا.")
        FullWidthButton("حفظ", enabled = !saving, onClick = {
            saving = true
            scope.launch {
                val saved = state.write { it.saveProduct(v, id) }
                saving = false
                if (saved != null) nav.pop()
            }
        })
        if (p != null) {
            FullWidthButton(if (p.active) "أرشفة المنتج" else "إعادة تنشيط المنتج", outlined = true, onClick = { confirmArchive = true })
        }
    }
    if (confirmArchive && p != null) {
        val action = if (p.active) "أرشفة" else "إعادة تنشيط"
        ConfirmDialog(action, "$action: ${p.name}؟\nسيُحتفظ بالبيانات وسجل التعاملات.", onConfirm = {
            scope.launch { if (state.write { it.archive("products", p.id, p.active) } != null) nav.pop() }
        }, onDismiss = { confirmArchive = false })
    }
}
