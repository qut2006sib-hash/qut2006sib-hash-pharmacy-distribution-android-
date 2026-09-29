package com.localdistribution.pharmacy.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.localdistribution.pharmacy.core.Clock
import com.localdistribution.pharmacy.core.Exports
import com.localdistribution.pharmacy.core.Labels
import com.localdistribution.pharmacy.core.Order
import com.localdistribution.pharmacy.core.OrderInput
import com.localdistribution.pharmacy.core.OrderLineInput
import com.localdistribution.pharmacy.core.Pharmacy
import com.localdistribution.pharmacy.core.Product
import com.localdistribution.pharmacy.core.Validate
import com.localdistribution.pharmacy.platform.Printing
import kotlinx.coroutines.launch

private fun money(minor: Long) = ltr(Validate.moneyText(minor))

@Composable
fun OrdersScreen(state: AppState, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val data = rememberData(state) { it.orders() }
    Box(Modifier.fillMaxSize()) {
        Column {
            SearchBar(query, { query = it })
            Loaded(data) { all ->
                val rows = all.filter { matches(query, it.number, it.pharmacyName, it.orderDate, Labels.ORDER_STATES[it.status], it.notes, it.currency) }
                if (rows.isEmpty()) Text("لا توجد طلبيات.", Modifier.padding(24.dp))
                LazyColumn(contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.id }) { o ->
                        InfoCard(onClick = { nav.push(Screen.OrderDetail(o.id)) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(ltr(o.number), fontWeight = FontWeight.Bold)
                                Badge(Labels.ORDER_STATES[o.status] ?: o.status, statusColor(o.status))
                            }
                            Text(o.pharmacyName)
                            Text("${ltr(o.orderDate)} · سعر الطلبية: ${money(o.orderPriceMinor)} ${o.currency}")
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { nav.push(Screen.OrderEdit(null, null)) }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Icon(Icons.Default.Add, "طلبية جديدة")
        }
    }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "draft" -> MaterialTheme.colorScheme.surfaceVariant
    "confirmed" -> MaterialTheme.colorScheme.secondaryContainer
    "delivered" -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.errorContainer
}

@Composable
fun OrderDetailScreen(state: AppState, nav: Nav, id: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<String?>(null) }
    val data = rememberData(state, id) { it.order(id) }
    Loaded(data) { o ->
        FormColumn {
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("بيان طلبية ${ltr(o.number)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Badge(Labels.ORDER_STATES[o.status] ?: o.status, statusColor(o.status))
                }
                Text("الصيدلية: ${o.pharmacyName}")
                if (o.pharmacyPhone.isNotBlank()) Text("الهاتف: ${ltr(o.pharmacyPhone)}")
                if (o.pharmacyAddress.isNotBlank()) Text("العنوان: ${o.pharmacyAddress}")
                Text("التاريخ: ${ltr(o.orderDate)} · العملة: ${o.currency}")
            }
            InfoCard {
                o.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(item.productName, fontWeight = FontWeight.Bold)
                    Hint(ltr(item.productSku))
                    Text("${ltr(item.quantity.toString())} × ${money(item.unitPriceMinor)} = ${money(item.lineTotalMinor)}")
                }
            }
            InfoCard {
                Text("سعر الطلبية (مجموع المنتجات): ${money(o.orderPriceMinor)} ${o.currency}", fontWeight = FontWeight.Bold)
                Text("الخصم: ${money(o.discountMinor)}")
                Text("الإجمالي بعد الخصم: ${money(o.totalMinor)} ${o.currency}", fontWeight = FontWeight.Bold)
            }
            if (o.notes.isNotBlank()) InfoCard { Text(o.notes) }
            Hint("هذا البيان يسجّل تفاصيل الطلبية وحالتها. حالة التحصيل غير مسجلة في هذه النسخة.")
            if (o.status == "draft") {
                FullWidthButton("تعديل المسودة", outlined = true, onClick = { nav.push(Screen.OrderEdit(o.id, null)) })
                FullWidthButton("تأكيد", onClick = { target = "confirmed" })
            }
            if (o.status == "confirmed") FullWidthButton("تسجيل التسليم", onClick = { target = "delivered" })
            if (o.status == "draft" || o.status == "confirmed") FullWidthButton("إلغاء الطلبية", outlined = true, onClick = { target = "cancelled" })
            FullWidthButton("طباعة / حفظ PDF", outlined = true, onClick = { Printing.print(context, o.number, Exports.orderHtml(o)) })
        }
        target?.let { t ->
            var question = "تغيير حالة الطلبية إلى: ${Labels.ORDER_STATES[t]}؟"
            if (t == "confirmed") question += "\nبعد التأكيد تُقفل البنود والأسعار لحفظ تاريخ التعامل."
            ConfirmDialog("حالة الطلبية", question, onConfirm = {
                scope.launch { state.write { it.transitionOrder(o.id, t) } }
            }, onDismiss = { target = null })
        }
    }
}

private class Line(val productId: Long, val name: String, quantity: String, price: String) {
    var quantity by mutableStateOf(quantity)
    var price by mutableStateOf(price)
}

@Composable
fun OrderEditScreen(state: AppState, nav: Nav, id: Long?, pharmacyId: Long?) {
    val data = rememberData(state, id) { s -> Triple(id?.let { s.order(it) }, s.pharmacies(), s.products()) }
    Loaded(data) { (order, pharmacies, products) ->
        when {
            order != null && order.status != "draft" -> Text("تعديل البنود والأسعار متاح للمسودات فقط.", Modifier.padding(24.dp))
            pharmacies.none { it.active } || products.none { it.active } -> Text("أضف صيدلية نشطة ومنتجًا نشطًا أولًا.", Modifier.padding(24.dp))
            else -> OrderForm(state, nav, order, pharmacies, products, pharmacyId)
        }
    }
}

@Composable
private fun OrderForm(state: AppState, nav: Nav, o: Order?, pharmacies: List<Pharmacy>, products: List<Product>, pharmacyId: Long?) {
    val scope = rememberCoroutineScope()
    val choices = pharmacies.filter { it.active || it.id == o?.pharmacyId }
    var pharmacy by remember(o) { mutableStateOf(o?.pharmacyId ?: pharmacyId?.takeIf { id -> choices.any { it.id == id } } ?: choices.first().id) }
    var date by remember(o) { mutableStateOf(o?.orderDate ?: Clock.today()) }
    var currency by remember(o) { mutableStateOf(o?.currency ?: "SYP") }
    var discount by remember(o) { mutableStateOf(Validate.moneyText(o?.discountMinor ?: 0)) }
    var notes by remember(o) { mutableStateOf(o?.notes ?: "") }
    val lines = remember(o) {
        mutableStateListOf<Line>().apply {
            o?.items?.forEach { add(Line(it.productId, it.productName, it.quantity.toString(), Validate.moneyText(it.unitPriceMinor))) }
        }
    }
    var picked by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    val signature = { listOf(pharmacy, date, currency, discount, notes, lines.map { Triple(it.productId, it.quantity, it.price) }) }
    val initial = remember(o) { signature() }
    UnsavedChangesGuard(dirty = signature() != initial, nav = nav)

    val available = products.filter { it.active && it.currency == currency }
    FormColumn {
        Dropdown("الصيدلية *", choices.map { it.id to (it.name + if (!it.active) " — مؤرشفة" else "") }, pharmacy, { pharmacy = it })
        DateField("تاريخ الطلبية", date, { date = it }, optional = false)
        Dropdown("العملة", Labels.CURRENCIES.toList(), currency, { currency = it; picked = null }, enabled = lines.isEmpty())
        SectionTitle("المنتجات")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dropdown("اختر منتجًا", available.map { it.id to "${it.name} — ${it.sku} — ${Validate.moneyText(it.unitPriceMinor)}" },
                picked, { picked = it }, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val product = available.firstOrNull { it.id == picked } ?: return@OutlinedButton
                if (lines.any { it.productId == product.id }) {
                    state.dialog = "المنتج موجود" to "عدّل كمية المنتج في البند الموجود."
                } else {
                    lines.add(Line(product.id, product.name, "1", Validate.moneyText(product.unitPriceMinor)))
                }
                picked = null
            }, modifier = Modifier.padding(start = 8.dp)) { Text("إضافة") }
        }
        if (lines.isEmpty()) Hint("أضف منتجًا واحدًا على الأقل.")
        lines.forEach { line ->
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { lines.remove(line) }) { Icon(Icons.Default.Delete, "إزالة") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("الكمية", line.quantity, { line.quantity = it }, number = true, modifier = Modifier.weight(1f))
                    Field("سعر الوحدة", line.price, { line.price = it }, number = true, modifier = Modifier.weight(1f))
                }
                val total = runCatching { Validate.quantity(line.quantity) * Validate.money(line.price) }.getOrNull()
                Hint("المجموع: " + (total?.let { money(it) } ?: "—"))
            }
        }
        Field("خصم الطلبية", discount, { discount = it }, number = true)
        val summary = runCatching {
            val subtotal = lines.sumOf { Validate.quantity(it.quantity) * Validate.money(it.price) }
            val d = Validate.money(discount)
            require(d <= subtotal)
            "المجموع: ${money(subtotal)}     الخصم: ${money(d)}     الإجمالي: ${money(subtotal - d)} $currency"
        }.getOrElse { "أكمل الأسعار والخصم بقيم صحيحة لحساب الإجمالي." }
        InfoCard { Text(summary, fontWeight = FontWeight.Bold) }
        Field("الملاحظات", notes, { notes = it }, lines = 3)
        Hint("بعد حفظ المسودة، يمكنك تأكيدها من صفحة الطلبية. تأكيد الطلبية يثبّت بنودها وأسعارها.")
        FullWidthButton("حفظ كمسودة", enabled = !saving, onClick = {
            saving = true
            val input = OrderInput(pharmacy, date, currency, discount, notes, lines.map { OrderLineInput(it.productId, it.quantity, it.price) })
            scope.launch {
                val saved = state.write { it.saveOrder(input, o?.id) }
                saving = false
                if (saved != null) {
                    if (o == null) nav.replace(Screen.OrderDetail(saved)) else nav.pop()
                }
            }
        })
    }
}
