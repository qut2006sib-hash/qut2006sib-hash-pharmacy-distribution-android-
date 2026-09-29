package com.localdistribution.pharmacy.core

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exports {
    private val TABLES = listOf("pharmacies", "products", "contacts", "orders", "order_items", "audit_events")

    fun spreadsheetSafe(value: Any?): Any? {
        if (value is String && value.trimStart().let { s -> listOf("=", "+", "-", "@", "\t", "\r").any { s.startsWith(it) } }) {
            return "'$value"
        }
        return value
    }

    /** Python csv/str rendering: True/False for booleans, empty for None. */
    private fun cell(value: Any?): String {
        val text = when (value) {
            null -> ""
            is Boolean -> if (value) "True" else "False"
            else -> value.toString()
        }
        val needsQuotes = text.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    /** One consistent read; JSON preserves exact text, CSV is spreadsheet-safe. Same layout as the desktop export. */
    fun exportAll(db: AppDatabase, destination: File, version: String): File {
        if (destination.exists()) throw DataSafetyError("اختر اسمًا جديدًا حتى لا تستبدل تصديرًا سابقًا.")
        val tables = db.read { c ->
            val items = c.query("SELECT * FROM order_items ORDER BY id DESC", mapper = OrderItem::from)
            linkedMapOf<String, Any?>(
                "pharmacies" to c.query("SELECT * FROM pharmacies ORDER BY id DESC") { Pharmacy.from(it).record() },
                "products" to c.query("SELECT * FROM products ORDER BY id DESC") { Product.from(it).record() },
                "contacts" to c.query("SELECT * FROM contacts ORDER BY id DESC") { Contact.from(it).record() },
                "orders" to c.query("SELECT * FROM orders ORDER BY id DESC") { Order.from(it, emptyList()).columns() },
                "order_items" to items.map { it.record() },
                "audit_events" to c.query("SELECT * FROM audit_events ORDER BY id DESC") { AuditEvent.from(it).record() },
            )
        }
        val payload = linkedMapOf("application" to "pharmacy_distribution", "version" to version, "exported_at" to Clock.now(),
            "money_scale" to 100, "tables" to tables)
        val parent = destination.absoluteFile.parentFile.also { it.mkdirs() }
        val temp = File(parent, "." + destination.name + "." + UUID.randomUUID().toString().replace("-", ""))
        try {
            FileOutputStream(temp).use { stream ->
                ZipOutputStream(stream).let { zip ->
                    fun put(name: String, bytes: ByteArray) {
                        zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                    }
                    put("all_data.json", Json.pretty(payload).toByteArray(Charsets.UTF_8))
                    put("README.txt", ("JSON preserves exact data, including archived records and audit history.\n" +
                        "Money fields ending in _minor are integer hundredths; divide by 100 for display.\n" +
                        "CSV text that could become a spreadsheet formula is prefixed with an apostrophe.\n" +
                        "Use a .sqlite3 backup for full application restoration.\n").toByteArray(Charsets.UTF_8))
                    for (table in TABLES) {
                        val fields = Schema.COLUMNS.getValue(table)
                        val out = StringBuilder()
                        out.append(fields.joinToString(",") { cell(it) }).append("\r\n")
                        @Suppress("UNCHECKED_CAST")
                        for (row in tables[table] as List<Map<String, Any?>>) {
                            out.append(fields.joinToString(",") { cell(spreadsheetSafe(row[it])) }).append("\r\n")
                        }
                        // utf-8-sig, like the desktop, so Excel reads Arabic correctly.
                        put("$table.csv", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + out.toString().toByteArray(Charsets.UTF_8))
                    }
                    // Finish the archive, then fsync before closing the file descriptor.
                    zip.finish()
                    zip.flush()
                    stream.fd.sync()
                }
            }
            Files2.publishExclusive(temp, destination)
        } finally {
            if (temp.exists()) temp.delete()
        }
        return destination
    }

    private fun esc(value: Any?): String = (value?.toString() ?: "").replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#x27;")

    private fun ltr(value: Any?) = "‎" + esc(value) + "‎"

    /** The printable order statement, same content as the desktop's order_html. */
    fun orderHtml(order: Order): String {
        val m = Validate::moneyText
        val rows = order.items.joinToString("") {
            "<tr><td>${esc(it.productName)}<br>${esc(it.productSku)}</td><td>${it.quantity}</td>" +
                "<td>${m(it.unitPriceMinor)}</td><td>${m(it.lineTotalMinor)}</td></tr>"
        }
        return """<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{font-family:sans-serif;font-size:11pt;margin:16px} table{border-collapse:collapse;width:100%} td,th{border:1px solid #444;padding:7px;text-align:right}</style></head>
<body dir="rtl">
<h2>بيان طلبية ${esc(order.number)}</h2>
<p><b>الصيدلية:</b> ${esc(order.pharmacyName)}<br>
<b>الهاتف:</b> ${ltr(order.pharmacyPhone)}<br><b>العنوان:</b> ${esc(order.pharmacyAddress)}<br>
<b>التاريخ:</b> ${ltr(order.orderDate)}<br><b>الحالة:</b> ${Labels.ORDER_STATES[order.status] ?: esc(order.status)}<br>
<b>العملة:</b> ${esc(order.currency)}</p>
<table><tr><th>المنتج</th><th>الكمية</th><th>سعر الوحدة</th><th>المجموع</th></tr>$rows</table>
<p><b>سعر الطلبية (مجموع المنتجات): ${m(order.orderPriceMinor)} ${esc(order.currency)}</b><br>الخصم: ${m(order.discountMinor)}<br>
الإجمالي بعد الخصم: ${m(order.totalMinor)} ${esc(order.currency)}</p>
<p>${esc(order.notes).replace("\n", "<br>")}</p>
<p>هذا البيان يسجّل تفاصيل الطلبية وحالتها. حالة التحصيل غير مسجلة في هذه النسخة.</p>
</body></html>"""
    }
}
