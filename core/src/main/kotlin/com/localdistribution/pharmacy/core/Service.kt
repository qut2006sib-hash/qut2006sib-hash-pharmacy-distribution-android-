package com.localdistribution.pharmacy.core

import java.text.Normalizer
import java.util.UUID

data class PharmacyInput(
    val name: String = "", val phone: String = "", val contactName: String = "", val area: String = "",
    val address: String = "", val stage: String = "new", val contractStatus: String = "none",
    val followUpOn: String = "", val notes: String = "",
)

data class ProductInput(
    val name: String = "", val sku: String = "", val brand: String = "", val category: String = "", val size: String = "",
    val currency: String = "SYP", val price: String = "0", val notes: String = "",
)

data class ContactInput(
    val pharmacyId: Long, val occurredAt: String, val kind: String, val note: String, val nextFollowUpOn: String = "",
)

data class OrderInput(
    val pharmacyId: Long, val orderDate: String, val currency: String, val discount: String = "0",
    val notes: String = "", val items: List<OrderLineInput>,
)

/** Port of the desktop Service: every write is validated, transactional and audited. */
class Service(private val db: AppDatabase) {

    // ---------- reads ----------

    fun pharmacies(includeArchived: Boolean = true): List<Pharmacy> = db.read { c ->
        c.query("SELECT * FROM pharmacies ORDER BY id DESC", mapper = Pharmacy::from).filter { includeArchived || it.active }
    }

    fun products(includeArchived: Boolean = true): List<Product> = db.read { c ->
        c.query("SELECT * FROM products ORDER BY id DESC", mapper = Product::from).filter { includeArchived || it.active }
    }

    fun contacts(): List<Contact> = db.read { c -> c.query("SELECT * FROM contacts ORDER BY id DESC", mapper = Contact::from) }

    fun contactsFor(pharmacyId: Long): List<Contact> = db.read { c ->
        c.query("SELECT * FROM contacts WHERE pharmacy_id = ? ORDER BY id DESC", pharmacyId, mapper = Contact::from)
    }

    fun orders(): List<Order> = db.read { c ->
        val items = c.query("SELECT * FROM order_items ORDER BY id", mapper = OrderItem::from).groupBy { it.orderId }
        c.query("SELECT * FROM orders ORDER BY id DESC") { Order.from(it, items[it.req("id")] ?: emptyList()) }
    }

    fun auditEvents(limit: Int = 1000): List<AuditEvent> = db.read { c ->
        c.query("SELECT * FROM audit_events ORDER BY id DESC LIMIT $limit", mapper = AuditEvent::from)
    }

    fun pharmacy(id: Long): Pharmacy = db.read { c -> requirePharmacy(c, id) }
    fun product(id: Long): Product = db.read { c -> requireProduct(c, id) }
    fun contact(id: Long): Contact = db.read { c -> require(c.first("SELECT * FROM contacts WHERE id = ?", id, mapper = Contact::from)) }
    fun order(id: Long): Order = db.read { c -> requireOrder(c, id) }
    fun auditEvent(id: Long): AuditEvent = db.read { c -> require(c.first("SELECT * FROM audit_events WHERE id = ?", id, mapper = AuditEvent::from)) }

    fun dashboard(): Dashboard = db.read { c ->
        fun count(sql: String) = c.first(sql) { it.long("n") } ?: 0L
        Dashboard(
            pharmacies = count("SELECT COUNT(*) AS n FROM pharmacies WHERE active = 1"),
            contracted = count("SELECT COUNT(*) AS n FROM pharmacies WHERE active = 1 AND contract_status = 'active'"),
            pending = count("SELECT COUNT(*) AS n FROM orders WHERE status IN ('draft', 'confirmed')"),
            followups = c.query("SELECT * FROM pharmacies WHERE active = 1 AND follow_up_on <= ? ORDER BY follow_up_on",
                Clock.today(), mapper = Pharmacy::from),
            totals = c.query("SELECT currency, status, COUNT(*) AS n, SUM(total_minor) AS total FROM orders GROUP BY currency, status") {
                CurrencyTotal(it.text("currency"), it.text("status"), it.req("n"), it.long("total") ?: 0L)
            },
        )
    }

    // ---------- writes ----------

    fun savePharmacy(values: PharmacyInput, rowId: Long? = null): Long {
        val name = Validate.text(values.name, "اسم الصيدلية", 200, true)
        val phone = Validate.text(values.phone, "الهاتف", 100)
        val contactName = Validate.text(values.contactName, "مسؤول التواصل", 200)
        val area = Validate.text(values.area, "المنطقة", 200)
        val address = Validate.text(values.address, "العنوان", 4000)
        val notes = Validate.text(values.notes, "الملاحظات", 20000)
        val stage = Validate.choice(values.stage, Labels.STAGES)
        val contract = Validate.choice(values.contractStatus, Labels.CONTRACTS)
        val followUp = Validate.isoDate(values.followUpOn, optional = true)
        return db.write { c ->
            val now = Clock.now()
            if (rowId != null) {
                val before = requirePharmacy(c, rowId)
                c.execute("""UPDATE pharmacies SET name=?, phone=?, contact_name=?, area=?, address=?, notes=?, stage=?,
                    contract_status=?, follow_up_on=?, updated_at=? WHERE id=?""",
                    name, phone, contactName, area, address, notes, stage, contract, followUp, now, rowId)
                audit(c, "update", "pharmacies", rowId, before.record(), requirePharmacy(c, rowId).record())
                rowId
            } else {
                val id = insertPharmacy(c, name, phone, contactName, area, address, stage, contract, followUp, notes, now)
                audit(c, "create", "pharmacies", id, null, requirePharmacy(c, id).record())
                id
            }
        }
    }

    fun saveProduct(values: ProductInput, rowId: Long? = null): Long {
        val name = Validate.text(values.name, "اسم المنتج", 200, true)
        val brand = Validate.text(values.brand, "العلامة", 100)
        val category = Validate.text(values.category, "الفئة", 100)
        val size = Validate.text(values.size, "الحجم", 100)
        val notes = Validate.text(values.notes, "الملاحظات", 20000)
        val sku = Validate.text(values.sku, "رمز المنتج", 100).ifEmpty { "P-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase() }
        val currency = Validate.choice(values.currency, Labels.CURRENCIES)
        val price = Validate.money(values.price)
        return db.write { c ->
            val existing = c.first("SELECT id FROM products WHERE sku = ?", sku) { it.req("id") }
            if (existing != null && existing != rowId) throw ValidationError("رمز المنتج مستخدم لمنتج آخر.")
            val now = Clock.now()
            if (rowId != null) {
                val before = requireProduct(c, rowId)
                c.execute("""UPDATE products SET name=?, brand=?, category=?, size=?, notes=?, sku=?, currency=?,
                    unit_price_minor=?, updated_at=? WHERE id=?""", name, brand, category, size, notes, sku, currency, price, now, rowId)
                audit(c, "update", "products", rowId, before.record(), requireProduct(c, rowId).record())
                rowId
            } else {
                val id = insertProduct(c, sku, name, brand, category, size, currency, price, notes, now)
                audit(c, "create", "products", id, null, requireProduct(c, id).record())
                id
            }
        }
    }

    /** Archiving keeps data and history; only pharmacies and products can be archived. */
    fun archive(entity: String, rowId: Long, archived: Boolean) {
        if (entity != "pharmacies" && entity != "products") throw ValidationError("الأرشفة غير مسموحة لهذا السجل.")
        db.write { c ->
            val before = if (entity == "pharmacies") requirePharmacy(c, rowId).record() else requireProduct(c, rowId).record()
            c.execute("UPDATE $entity SET active=?, updated_at=? WHERE id=?", !archived, Clock.now(), rowId)
            val after = if (entity == "pharmacies") requirePharmacy(c, rowId).record() else requireProduct(c, rowId).record()
            audit(c, if (archived) "archive" else "unarchive", entity, rowId, before, after)
        }
    }

    fun saveContact(values: ContactInput, rowId: Long? = null): Long {
        val occurredAt = Validate.isoDateTime(values.occurredAt)
        val kind = Validate.choice(values.kind, Labels.KINDS)
        val note = Validate.text(values.note, "نتيجة التواصل", 20000, true)
        val next = Validate.isoDate(values.nextFollowUpOn, optional = true)
        return db.write { c ->
            val pharmacy = requirePharmacy(c, values.pharmacyId)
            val now = Clock.now()
            val id: Long
            if (rowId != null) {
                val before = require(c.first("SELECT * FROM contacts WHERE id = ?", rowId, mapper = Contact::from))
                if (before.pharmacyId != pharmacy.id) throw ValidationError("لا يمكن نقل سجل تواصل إلى صيدلية أخرى.")
                c.execute("UPDATE contacts SET occurred_at=?, kind=?, note=?, next_follow_up_on=?, updated_at=? WHERE id=?",
                    occurredAt, kind, note, next, now, rowId)
                id = rowId
                audit(c, "update", "contacts", id, before.record(), contactRecord(c, id))
            } else {
                id = c.insert("""INSERT INTO contacts (pharmacy_id, occurred_at, kind, note, next_follow_up_on, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""", pharmacy.id, occurredAt, kind, note, next, now, now)
                audit(c, "create", "contacts", id, null, contactRecord(c, id))
                // Editing an old contact must not overwrite today's follow-up plan; only new ones do.
                val stage = if (pharmacy.stage == "new") "contacted" else pharmacy.stage
                c.execute("UPDATE pharmacies SET follow_up_on=?, stage=?, updated_at=? WHERE id=?", next, stage, Clock.now(), pharmacy.id)
                audit(c, "follow_up", "pharmacies", pharmacy.id, pharmacy.record(), requirePharmacy(c, pharmacy.id).record())
            }
            id
        }
    }

    fun saveOrder(values: OrderInput, rowId: Long? = null): Long {
        val currency = Validate.choice(values.currency, Labels.CURRENCIES)
        val orderDate = Validate.isoDate(values.orderDate)!!
        val discount = Validate.money(values.discount)
        val notes = Validate.text(values.notes, "الملاحظات", 20000)
        val lines = values.items
        if (lines.isEmpty()) throw ValidationError("أضف منتجًا واحدًا على الأقل إلى الطلبية.")
        if (lines.size > 500) throw ValidationError("الحد الأقصى 500 بند في الطلبية الواحدة.")
        return db.write { c ->
            val pharmacy = requirePharmacy(c, values.pharmacyId)
            if (!pharmacy.active) throw ValidationError("الصيدلية مؤرشفة؛ أعد تنشيطها قبل تسجيل طلبية.")
            val before = rowId?.let { requireOrder(c, it) }
            if (before != null && before.status != "draft") {
                throw ValidationError("تعديل البنود متاح للمسودات فقط. الطلبية المؤكدة محفوظة بتاريخها وأسعارها.")
            }
            data class Line(val product: Product, val quantity: Long, val price: Long, val total: Long)
            val prepared = mutableListOf<Line>()
            val seen = mutableSetOf<Long>()
            var subtotal = 0L
            for (line in lines) {
                val product = requireProduct(c, line.productId)
                if (!product.active) throw ValidationError("المنتج مؤرشف: ${product.name}")
                if (product.currency != currency) throw ValidationError("يجب أن تكون جميع منتجات الطلبية بالعملة نفسها.")
                if (!seen.add(product.id)) throw ValidationError("المنتج مكرر؛ عدّل كميته في البند الموجود.")
                val qty = Validate.quantity(line.quantity)
                val price = Validate.money(line.price)
                // qty ≤ 1e6 and price ≤ 1e14, so this cannot overflow a Long; checked anyway.
                if (price != 0L && qty > Long.MAX_VALUE / price) throw ValidationError("إجمالي الطلبية يتجاوز الحد المدعوم.")
                val total = qty * price
                subtotal += total
                if (subtotal > MAX_MONEY) throw ValidationError("إجمالي الطلبية يتجاوز الحد المدعوم.")
                prepared += Line(product, qty, price, total)
            }
            if (discount > subtotal) throw ValidationError("الخصم أكبر من قيمة الطلبية.")
            val now = Clock.now()
            val id = if (before != null) {
                c.execute("DELETE FROM order_items WHERE order_id = ?", before.id)
                c.execute("""UPDATE orders SET pharmacy_id=?, pharmacy_name=?, pharmacy_phone=?, pharmacy_address=?, currency=?,
                    order_date=?, notes=?, status='draft', subtotal_minor=?, discount_minor=?, total_minor=?, updated_at=? WHERE id=?""",
                    pharmacy.id, pharmacy.name, pharmacy.phone, pharmacy.address, currency, orderDate, notes,
                    subtotal, discount, subtotal - discount, now, before.id)
                before.id
            } else {
                c.insert("""INSERT INTO orders (pharmacy_id, pharmacy_name, pharmacy_phone, pharmacy_address, order_date, currency,
                    status, subtotal_minor, discount_minor, total_minor, notes, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'draft', ?, ?, ?, ?, ?, ?)""",
                    pharmacy.id, pharmacy.name, pharmacy.phone, pharmacy.address, orderDate, currency,
                    subtotal, discount, subtotal - discount, notes, now, now)
            }
            for (line in prepared) insertItem(c, id, line.product, line.quantity, line.price)
            audit(c, if (before != null) "update" else "create", "orders", id, before?.record(), requireOrder(c, id).record())
            id
        }
    }

    fun transitionOrder(rowId: Long, target: String) {
        val transitions = mapOf("draft" to setOf("confirmed", "cancelled"), "confirmed" to setOf("delivered", "cancelled"),
            "delivered" to emptySet(), "cancelled" to emptySet())
        db.write { c ->
            val before = requireOrder(c, rowId)
            if (target !in transitions.getValue(before.status)) throw ValidationError("هذا الانتقال غير مسموح لحالة الطلبية الحالية.")
            c.execute("UPDATE orders SET status=?, updated_at=? WHERE id=?", target, Clock.now(), rowId)
            audit(c, "status", "orders", rowId, before.record(), requireOrder(c, rowId).record())
        }
    }

    fun recordRestore(sourceName: String, preserved: String?) {
        db.write { c -> audit(c, "restore", "database", null, null, linkedMapOf("source" to sourceName, "previous_data" to (preserved ?: "None"))) }
    }

    // ---------- one-time imports (same markers as desktop, so they never run twice) ----------

    fun seedInitialPharmacies(): List<String> {
        val names = listOf("ساكورا", "مادورا", "أدورا", "ديمة", "المنارة")
        val aliases = mapOf("ساكورا" to setOf("ساكورا"), "مادورا" to setOf("مادورا"), "أدورا" to setOf("أدورا", "ادورا"),
            "ديمة" to setOf("ديمة", "ديمه"), "المنارة" to setOf("المنارة", "منارة", "المناره", "مناره"))
        if (!FieldData.ENABLED) return emptyList()
        return db.write { c ->
            if (hasMarker(c, "initial_pharmacy_import")) return@write emptyList<String>()
            val existing = c.query("SELECT name FROM pharmacies ORDER BY id") { nameKey(it.text("name")) }.toMutableSet()
            val beforeAll = existing.sorted()
            val added = mutableListOf<String>()
            val provided = mutableListOf<Map<String, Any?>>()
            for (name in names) {
                val keys = (aliases[name] ?: setOf(name)).map(::nameKey).toSet()
                if (keys.any { it in existing }) {
                    provided += linkedMapOf("name" to name, "result" to "already_present"); continue
                }
                val id = insertPharmacy(c, name, "", "", "", "", "new", "none", null,
                    "اسم وارد ضمن قائمة الصيدليات التي زوّد بها المستخدم؛ بقية البيانات لم تُزوّد.", Clock.now())
                audit(c, "create", "pharmacies", id, null, requirePharmacy(c, id).record())
                existing.addAll(keys)
                added += name
                provided += linkedMapOf("name" to name, "result" to "added")
            }
            audit(c, "initial_pharmacy_import", "system", null,
                linkedMapOf("existing_names_normalized" to beforeAll),
                linkedMapOf("provided_pharmacies" to provided, "added_count" to added.size))
            added
        }
    }

    fun seedFieldData(): Map<String, Any?> {
        val marker = "field_data_import_2026_09_26"
        if (!FieldData.ENABLED) return mapOf("already_imported" to true)
        val legacy = setOf("ساكورا", "مادورا", "أدورا", "ديمة", "المنارة")
        val placeholder = "اسم وارد ضمن قائمة الصيدليات التي زوّد بها المستخدم؛ بقية البيانات لم تُزوّد."
        val summary = linkedMapOf<String, Any?>("pharmacies_created" to 0, "pharmacies_updated" to 0, "products_created" to 0, "orders_created" to 0)
        val done = db.write { c ->
            if (hasMarker(c, marker)) return@write true
            val rows = c.query("SELECT * FROM pharmacies ORDER BY id", mapper = Pharmacy::from).toMutableList()
            val byName = mutableMapOf<String, Pharmacy>()
            for (item in FieldData.PHARMACIES) {
                val keys = (listOf(item.name) + item.aliases).map(::nameKey).toSet()
                val index = rows.indexOfFirst { nameKey(it.name) in keys }
                var row: Pharmacy
                val before: Pharmacy?
                if (index < 0) {
                    before = null
                    val id = insertPharmacy(c, item.name, "", "", "", "", item.stage, item.contractStatus, null, "", Clock.now())
                    row = requirePharmacy(c, id)
                    rows += row
                    summary["pharmacies_created"] = (summary["pharmacies_created"] as Int) + 1
                } else {
                    before = rows[index]
                    row = before
                    if (row.name in legacy) row = row.copy(name = item.name)
                    if (row.stage == "new" && item.stage != "new") row = row.copy(stage = item.stage)
                    if (row.contractStatus == "none" && item.contractStatus != "none") row = row.copy(contractStatus = item.contractStatus)
                }
                if (item.phone.isNotEmpty() && row.phone.isEmpty()) row = row.copy(phone = item.phone)
                if (item.address.isNotEmpty() && row.address.isEmpty()) row = row.copy(address = item.address)
                val imported = item.notes.joinToString("\n")
                if (imported.isNotEmpty()) {
                    if (row.notes.isEmpty() || row.notes == placeholder) row = row.copy(notes = imported)
                    else if (!row.notes.contains(imported)) row = row.copy(notes = row.notes.trimEnd() + "\n\n" + imported)
                }
                row = row.copy(updatedAt = Clock.now())
                c.execute("UPDATE pharmacies SET name=?, stage=?, contract_status=?, phone=?, address=?, notes=?, updated_at=? WHERE id=?",
                    row.name, row.stage, row.contractStatus, row.phone, row.address, row.notes, row.updatedAt, row.id)
                row = requirePharmacy(c, row.id)
                rows[rows.indexOfFirst { it.id == row.id }] = row
                val after = row.record()
                if (before == null) {
                    audit(c, "create", "pharmacies", row.id, null, after)
                } else if (after != before.record()) {
                    audit(c, "update", "pharmacies", row.id, before.record(), after)
                    summary["pharmacies_updated"] = (summary["pharmacies_updated"] as Int) + 1
                }
                byName[item.name] = row
            }
            val products = mutableMapOf<String, Product>()
            val prices = mutableMapOf<String, Long>()
            for (item in FieldData.PRODUCTS) {
                var product = c.first("SELECT * FROM products WHERE sku = ?", item.sku, mapper = Product::from)
                if (product == null) {
                    val id = insertProduct(c, item.sku, item.name, item.brand, "", "", "USD", Validate.money(item.price),
                        "سعر الوحدة مستورد من القائمة التي زوّد بها المستخدم.", Clock.now())
                    product = requireProduct(c, id)
                    audit(c, "create", "products", id, null, product.record())
                    summary["products_created"] = (summary["products_created"] as Int) + 1
                }
                products[item.sku] = product
                prices[item.sku] = Validate.money(item.price)
            }
            val seed = FieldData.ORDER_1
            val pharmacy = byName.getValue(seed.pharmacy)
            var subtotal = 0L
            for ((sku, qty) in seed.items) subtotal += qty * prices.getValue(sku)
            val now = Clock.now()
            val orderId = c.insert("""INSERT INTO orders (pharmacy_id, pharmacy_name, pharmacy_phone, pharmacy_address, order_date,
                currency, status, subtotal_minor, discount_minor, total_minor, notes, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)""",
                pharmacy.id, pharmacy.name, pharmacy.phone, pharmacy.address, seed.orderDate, seed.currency, seed.status,
                subtotal, subtotal, seed.notes, now, now)
            for ((sku, qty) in seed.items) insertItem(c, orderId, products.getValue(sku), qty, prices.getValue(sku))
            audit(c, "create", "orders", orderId, null, requireOrder(c, orderId).record())
            summary["orders_created"] = 1
            audit(c, marker, "system", null, null, LinkedHashMap(summary))
            false
        }
        return if (done) mapOf("already_imported" to true) else summary.apply { put("already_imported", false) }
    }

    // ---------- helpers ----------

    private fun hasMarker(c: SqlConnection, action: String) =
        c.first("SELECT id FROM audit_events WHERE action = ? AND entity = 'system' LIMIT 1", action) { it.req("id") } != null

    private fun insertPharmacy(c: SqlConnection, name: String, phone: String, contactName: String, area: String, address: String,
                               stage: String, contract: String, followUp: String?, notes: String, now: String): Long =
        c.insert("""INSERT INTO pharmacies (name, phone, contact_name, area, address, stage, contract_status, follow_up_on, notes,
            active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)""",
            name, phone, contactName, area, address, stage, contract, followUp, notes, now, now)

    private fun insertProduct(c: SqlConnection, sku: String, name: String, brand: String, category: String, size: String,
                              currency: String, price: Long, notes: String, now: String): Long =
        c.insert("""INSERT INTO products (sku, name, brand, category, size, currency, unit_price_minor, notes, active, created_at,
            updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)""", sku, name, brand, category, size, currency, price, notes, now, now)

    private fun insertItem(c: SqlConnection, orderId: Long, product: Product, quantity: Long, price: Long) {
        c.insert("""INSERT INTO order_items (order_id, product_id, product_name, product_sku, product_brand, quantity,
            unit_price_minor, line_total_minor) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            orderId, product.id, product.name, product.sku, product.brand, quantity, price, quantity * price)
    }

    private fun audit(c: SqlConnection, action: String, entity: String, rowId: Long?, before: Any?, after: Any?) {
        c.insert("INSERT INTO audit_events (occurred_at, action, entity, entity_id, before_json, after_json) VALUES (?, ?, ?, ?, ?, ?)",
            Clock.now(), action, entity, rowId, Json.dumps(before), Json.dumps(after))
    }

    private fun contactRecord(c: SqlConnection, id: Long) = require(c.first("SELECT * FROM contacts WHERE id = ?", id, mapper = Contact::from)).record()
    private fun requirePharmacy(c: SqlConnection, id: Long) = require(c.first("SELECT * FROM pharmacies WHERE id = ?", id, mapper = Pharmacy::from))
    private fun requireProduct(c: SqlConnection, id: Long) = require(c.first("SELECT * FROM products WHERE id = ?", id, mapper = Product::from))
    private fun requireOrder(c: SqlConnection, id: Long): Order {
        val items = c.query("SELECT * FROM order_items WHERE order_id = ? ORDER BY id", id, mapper = OrderItem::from)
        return require(c.first("SELECT * FROM orders WHERE id = ?", id) { Order.from(it, items) })
    }

    private fun <T> require(row: T?): T = row ?: throw ValidationError("السجل غير موجود. حدّث القائمة ثم حاول مجددًا.")

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val PREFIX = Regex("^صيدليه(?:\\s+|$)")

        /** “صيدلية مادورا” and “مادورا” refer to the same entry. */
        fun nameKey(value: String?): String {
            var s = Normalizer.normalize(value ?: "", Normalizer.Form.NFKD)
            s = s.filter { ch ->
                val type = Character.getType(ch).toByte()
                type != Character.NON_SPACING_MARK && type != Character.ENCLOSING_MARK &&
                    type != Character.COMBINING_SPACING_MARK && ch != 'ـ'
            }
            s = s.map { when (it) { 'أ', 'إ', 'آ', 'ٱ' -> 'ا'; 'ى' -> 'ي'; 'ة' -> 'ه'; else -> it } }.joinToString("")
            s = s.lowercase().trim().split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
            return s.replace(PREFIX, "").trim()
        }
    }
}
