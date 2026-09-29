package com.localdistribution.pharmacy.core

import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Ports of the desktop tests/test_data_safety.py and test_field_data_import.py. */
class CoreTest {
    private val factory = JdbcFactory()
    private fun root(): File = Files.createTempDirectory("pd").toFile()
    private fun open(root: File = root()) = AppDatabase(root, factory)

    private class Seeded(val db: AppDatabase, val s: Service, val pharmacy: Long, val a: Long, val b: Long, val syp: Long)

    private fun seeded(): Seeded {
        val db = open()
        val s = Service(db)
        val p = s.savePharmacy(PharmacyInput(name = "صيدلية الأمل", phone = "+963111", area = "حمص"))
        val a = s.saveProduct(ProductInput(name = "منتج أ", sku = "A-1", brand = "X", currency = "USD", price = "10.25"))
        val b = s.saveProduct(ProductInput(name = "منتج ب", sku = "B-1", brand = "X", currency = "USD", price = "3.10"))
        val syp = s.saveProduct(ProductInput(name = "منتج ليرة", sku = "S-1", currency = "SYP", price = "5000"))
        return Seeded(db, s, p, a, b, syp)
    }

    private fun Seeded.order(discount: String = "0", lines: List<OrderLineInput> = listOf(
        OrderLineInput(a, "2", "10.25"), OrderLineInput(b, "3", "3.10"))) =
        OrderInput(pharmacy, "2026-09-25", "USD", discount, "ملاحظة", lines)

    private fun count(db: AppDatabase, table: String) = db.read { c -> c.first("SELECT COUNT(*) AS n FROM $table") { it.req("n") }!! }

    // ---------- validation ----------

    @Test fun decimalAmountsExact() {
        assertEquals(10, Validate.money("0.10"))
        assertEquals(1235, Validate.money("12.35"))
        assertEquals(1235, Validate.money("١٢٫٣٥"))
        assertEquals(10000, Validate.money("100"))
        assertEquals("12.05", Validate.moneyText(1205))
    }

    @Test fun invalidMoneyRejected() {
        for (value in listOf("-1", "NaN", "Infinity", "1.001", "1,200", "1e100", "", "abc")) {
            assertFailsWith<ValidationError>(value) { Validate.money(value) }
        }
    }

    @Test fun datesAndQuantities() {
        assertEquals("2026-09-25", Validate.isoDate("2026-09-25"))
        assertEquals(null, Validate.isoDate(" ", optional = true))
        assertFailsWith<ValidationError> { Validate.isoDate("2026-9-25") }
        assertFailsWith<ValidationError> { Validate.isoDate("2026-02-30") }
        assertEquals("2026-09-25T14:30", Validate.isoDateTime("2026-09-25 14:30"))
        assertEquals("2026-09-25T14:30", Validate.isoDateTime("2026-09-25T14:30:59"))
        assertFailsWith<ValidationError> { Validate.isoDateTime("yesterday") }
        assertEquals(5, Validate.quantity(" 5 "))
        assertFailsWith<ValidationError> { Validate.quantity("0") }
        assertFailsWith<ValidationError> { Validate.quantity("1000001") }
        assertFailsWith<ValidationError> { Validate.text("x".repeat(201), "الاسم") }
        assertFailsWith<ValidationError> { Validate.text("  ", "الاسم", required = true) }
    }

    @Test fun arabicNameKey() {
        assertEquals(Service.nameKey("مادورا"), Service.nameKey("صيدلية مادورا"))
        assertEquals(Service.nameKey("أدورا"), Service.nameKey("ادورا"))
        assertEquals(Service.nameKey("المنارة"), Service.nameKey("  المناره "))
        assertEquals(Service.nameKey("ديمة"), Service.nameKey("ديـمه"))
    }

    @Test fun jsonMatchesPython() {
        val record = linkedMapOf("id" to 1L, "name" to "ساكورا \"x\"\n", "follow_up_on" to null, "active" to true, "items" to listOf<Any>())
        assertEquals("{\"id\": 1, \"name\": \"ساكورا \\\"x\\\"\\n\", \"follow_up_on\": null, \"active\": true, \"items\": []}", Json.dumps(record))
        assertEquals("{\n  \"a\": [\n    1,\n    2\n  ],\n  \"b\": {}\n}", Json.pretty(linkedMapOf("a" to listOf(1, 2), "b" to emptyMap<String, Any>())))
        assertEquals(record, Json.parse(Json.dumps(record)))
    }

    // ---------- startup, seeding ----------

    @Test fun newDatabaseContainsOnlyUserSuppliedInitialNames() {
        val db = open()
        val s = Service(db)
        assertEquals(setOf("ساكورا", "مادورا", "أدورا", "ديمة", "المنارة"), s.pharmacies().map { it.name }.toSet())
        assertTrue(s.pharmacies().all { it.phone == "" && it.stage == "new" && it.contractStatus == "none" && "لم تُزوّد" in it.notes })
        assertTrue(s.products().isEmpty())
        assertEquals(emptyList(), s.seedInitialPharmacies())
        assertEquals(1, s.auditEvents().count { it.action == "initial_pharmacy_import" })
        assertEquals(Schema.CURRENT_REVISION, db.inspectCurrent().revision)
        assertTrue(db.backups.list().any { it.name.contains("_initial_") })
    }

    @Test fun fieldDataImportMatchesDesktopAndIsIdempotent() {
        val db = open()
        val s = Service(db)
        val summary = s.seedFieldData()
        // Same numbers the desktop reports for a fresh database.
        assertEquals(mapOf("pharmacies_created" to 9, "pharmacies_updated" to 5, "products_created" to 18, "orders_created" to 1,
            "already_imported" to false), summary)
        assertEquals(14, s.pharmacies().size)
        val order = s.orders().single()
        assertEquals(17265, order.totalMinor)
        assertEquals(17265, order.orderPriceMinor)
        assertEquals("confirmed", order.status)
        assertEquals("صيدلية ساكورا", order.pharmacyName)
        val sakura = s.pharmacies().first { it.name == "صيدلية ساكورا" }
        assertEquals("active", sakura.contractStatus)
        assertFalse("لم تُزوّد" in sakura.notes)
        assertEquals(mapOf("already_imported" to true), s.seedFieldData())
        assertEquals(1, s.orders().size)
    }

    @Test fun fieldDataImportPreservesExistingUserFields() {
        val db = open()
        val s = Service(db)
        val manara = s.pharmacies().first { it.name == "المنارة" }
        s.savePharmacy(PharmacyInput(name = "المنارة", phone = "0999", notes = "ملاحظتي"), manara.id)
        s.seedFieldData()
        val after = s.pharmacy(manara.id)
        assertEquals("صيدلية المنارة", after.name)
        assertEquals("0999", after.phone)
        assertTrue(after.notes.startsWith("ملاحظتي\n\n"))
    }

    @Test fun reopeningKeepsDataAndTakesStartupBackup() {
        val x = seeded()
        val id = x.s.saveOrder(x.order())
        val before = x.s.order(id)
        val root = x.db.root
        x.db.close()
        val db2 = open(root)
        assertEquals(before, Service(db2).order(id))
        assertTrue(db2.backups.list().any { it.name.contains("_startup_") })
    }

    // ---------- orders ----------

    @Test fun orderTotalsAndHistoricalSnapshots() {
        val x = seeded()
        val id = x.s.saveOrder(x.order(discount = "1.00"))
        val o = x.s.order(id)
        assertEquals(2050 + 930, o.subtotalMinor)
        assertEquals(100, o.discountMinor)
        assertEquals(o.subtotalMinor - 100, o.totalMinor)
        assertEquals("ORD-" + id.toString().padStart(6, '0'), o.number)
        // Changing the product later must not change the saved order.
        x.s.saveProduct(ProductInput(name = "اسم جديد", sku = "A-1", currency = "USD", price = "99"), x.a)
        assertEquals("منتج أ", x.s.order(id).items.first().productName)
        assertEquals(1025, x.s.order(id).items.first().unitPriceMinor)
    }

    @Test fun invalidOrderRulesDoNotCreateRecords() {
        val x = seeded()
        val audits = count(x.db, "audit_events")
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(lines = emptyList())) }
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(lines = listOf(OrderLineInput(x.a, "1", "1"), OrderLineInput(x.a, "1", "1")))) }
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(lines = listOf(OrderLineInput(x.syp, "1", "1")))) }
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(discount = "999")) }
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(lines = listOf(OrderLineInput(x.a, "0", "1")))) }
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(lines = listOf(OrderLineInput(9999, "1", "1")))) }
        x.s.archive("products", x.b, true)
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order()) }
        x.s.archive("pharmacies", x.pharmacy, true)
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(lines = listOf(OrderLineInput(x.a, "1", "1")))) }
        assertEquals(0, count(x.db, "orders"))
        assertEquals(0, count(x.db, "order_items"))
        assertEquals(audits + 2, count(x.db, "audit_events")) // only the two archive events
    }

    @Test fun draftEditReplacesItemsAndAuditsPreviousValues() {
        val x = seeded()
        val id = x.s.saveOrder(x.order())
        x.s.saveOrder(x.order(lines = listOf(OrderLineInput(x.b, "1", "3.10"))), id)
        val o = x.s.order(id)
        assertEquals(1, o.items.size)
        assertEquals(310, o.totalMinor)
        assertEquals(1, count(x.db, "order_items"))
        val event = x.s.auditEvents().first { it.entity == "orders" && it.action == "update" }
        @Suppress("UNCHECKED_CAST")
        val before = Json.parse(event.beforeJson) as Map<String, Any?>
        assertEquals(2, (before["items"] as List<*>).size)
        // A failed edit keeps the original items.
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(discount = "500"), id) }
        assertEquals(o, x.s.order(id))
    }

    @Test fun confirmedOrderLockedAndDeliveredTerminal() {
        val x = seeded()
        val id = x.s.saveOrder(x.order())
        x.s.transitionOrder(id, "confirmed")
        assertFailsWith<ValidationError> { x.s.saveOrder(x.order(), id) }
        x.s.transitionOrder(id, "delivered")
        assertFailsWith<ValidationError> { x.s.transitionOrder(id, "cancelled") }
        assertFailsWith<ValidationError> { x.s.transitionOrder(id, "draft") }
        assertEquals("delivered", x.s.order(id).status)
    }

    @Test fun dashboardCountsAndTotals() {
        val x = seeded()
        val id = x.s.saveOrder(x.order())
        x.s.saveOrder(x.order())
        x.s.transitionOrder(id, "confirmed")
        val d = x.s.dashboard()
        assertEquals(2, d.pending)
        assertEquals(setOf("draft", "confirmed"), d.totals.map { it.status }.toSet())
        assertEquals(2980, d.totals.first { it.status == "draft" }.totalMinor)
    }

    // ---------- contacts ----------

    @Test fun contactHistoryAndCurrentFollowupAreSeparate() {
        val x = seeded()
        val c1 = x.s.saveContact(ContactInput(x.pharmacy, "2026-09-20 10:00", "visit", "زيارة", "2026-10-01"))
        var p = x.s.pharmacy(x.pharmacy)
        assertEquals("2026-10-01", p.followUpOn)
        assertEquals("contacted", p.stage)
        // Correcting an old contact never moves the pharmacy's current plan.
        x.s.saveContact(ContactInput(x.pharmacy, "2026-09-20 10:00", "call", "تصحيح", "2026-12-12"), c1)
        p = x.s.pharmacy(x.pharmacy)
        assertEquals("2026-10-01", p.followUpOn)
        // A new contact with no date clears the plan.
        x.s.saveContact(ContactInput(x.pharmacy, "2026-09-21 09:00", "call", "اتصال"))
        assertEquals(null, x.s.pharmacy(x.pharmacy).followUpOn)
        val other = x.s.savePharmacy(PharmacyInput(name = "أخرى"))
        assertFailsWith<ValidationError> { x.s.saveContact(ContactInput(other, "2026-09-20 10:00", "visit", "نقل"), c1) }
        assertFailsWith<ValidationError> { x.s.saveContact(ContactInput(x.pharmacy, "2026-09-20 10:00", "visit", " ")) }
    }

    @Test fun overdueFollowupsOnDashboard() {
        val x = seeded()
        x.s.savePharmacy(PharmacyInput(name = "صيدلية الأمل", followUpOn = "2020-01-01"), x.pharmacy)
        assertEquals(listOf(x.pharmacy), x.s.dashboard().followups.map { it.id })
    }

    @Test fun foreignKeysRejectOrphans() {
        val x = seeded()
        x.s.saveOrder(x.order())
        assertFailsWith<Exception> { x.db.write { c -> c.execute("DELETE FROM pharmacies WHERE id = ?", x.pharmacy) } }
        assertFailsWith<Exception> { x.db.write { c -> c.execute("INSERT INTO contacts (pharmacy_id, occurred_at, kind, note, created_at, updated_at) VALUES (999, 'x', 'visit', 'n', 'x', 'x')") } }
    }

    @Test fun sqlLikeInputStoredAsText() {
        val db = open()
        val s = Service(db)
        val id = s.savePharmacy(PharmacyInput(name = "x'); DROP TABLE pharmacies; --"))
        assertEquals("x'); DROP TABLE pharmacies; --", s.pharmacy(id).name)
        db.inspectCurrent()
    }

    // ---------- backups, restore, compatibility ----------

    @Test fun everySaveLeavesAValidatedBackup() {
        val x = seeded()
        val newest = x.db.backups.list().first()
        assertTrue(newest.name.contains("_after_save_"))
        assertEquals(6, Inspector.inspect(newest, factory).pharmacies)
        assertEquals("", x.db.backups.lastError)
    }

    @Test fun restorePreservesPreviousDatabase() {
        val x = seeded()
        val backup = x.db.backup("manual")
        x.s.saveOrder(x.order())
        val preserved = x.db.restore(backup)
        assertNotNull(preserved)
        assertEquals(0, x.s.orders().size)
        assertEquals(1, Inspector.inspect(preserved, factory).orders)
        assertEquals(1, x.s.auditEvents().count { it.action == "restore" })
    }

    @Test fun corruptBackupRejectedWithoutTouchingCurrentData() {
        val x = seeded()
        val bad = File(x.db.root, "bad.sqlite3").apply { writeBytes(ByteArray(4096) { 7 }) }
        val empty = File(x.db.root, "empty.sqlite3").apply { writeBytes(ByteArray(0)) }
        val foreign = File(x.db.root, "foreign.sqlite3")
        factory.open(foreign, false).use { it.execute("CREATE TABLE t (x)") }
        val before = x.s.pharmacies()
        for (file in listOf(bad, empty, foreign)) {
            assertFailsWith<DataSafetyError>(file.name) { x.db.restore(file) }
        }
        assertEquals(before, x.s.pharmacies())
    }

    @Test fun corruptCurrentDatabaseIsPreservedDuringRecovery() {
        val x = seeded()
        val backup = x.db.backup("manual")
        val root = x.db.root
        x.db.close()
        File(root, "app.sqlite3").writeBytes(ByteArray(8192) { 1 })
        assertFailsWith<DataSafetyError> { open(root) }
        val preserved = AppDatabase.restoreWithoutOpening(root, factory, backup)
        assertTrue(preserved!!.isDirectory)
        assertTrue(File(preserved, "app.sqlite3").exists())
        assertEquals(6, Service(open(root)).pharmacies().size)
    }

    @Test fun existingBackupNeverOverwritten() {
        val x = seeded()
        val target = File(x.db.root, "keep.sqlite3").apply { writeText("keep") }
        assertFailsWith<DataSafetyError> { x.db.backup("external", target) }
        assertEquals("keep", target.readText())
    }

    @Test fun futureSchemaAndTotalMismatchRejected() {
        val x = seeded()
        x.s.saveOrder(x.order())
        val copy = x.db.backup("manual")
        val tampered = File(x.db.root, "tampered.sqlite3")
        copy.copyTo(tampered)
        factory.open(tampered, false).use { it.execute("UPDATE orders SET subtotal_minor = subtotal_minor + 1, total_minor = total_minor + 1") }
        assertFailsWith<DataSafetyError> { Inspector.inspect(tampered, factory) }
        val future = File(x.db.root, "future.sqlite3")
        copy.copyTo(future)
        factory.open(future, false).use { it.execute("UPDATE alembic_version SET version_num = '0002_future'") }
        assertFailsWith<DataSafetyError> { x.db.restore(future) }
        val withTrigger = File(x.db.root, "trigger.sqlite3")
        copy.copyTo(withTrigger)
        factory.open(withTrigger, false).use { it.execute("CREATE TRIGGER t AFTER INSERT ON pharmacies BEGIN SELECT 1; END") }
        assertFailsWith<DataSafetyError> { Inspector.inspect(withTrigger, factory) }
    }

    @Test fun opensBackupMadeByDesktopApp() {
        val source = File(javaClass.classLoader.getResource("desktop_backup.sqlite3")!!.toURI())
        val x = seeded()
        x.db.restore(source)
        val s = x.s
        val pharmacies = s.pharmacies()
        assertTrue(pharmacies.any { it.name == "صيدلية الاختبار" && it.phone == "+963000000" })
        assertTrue(pharmacies.any { it.name == "صيدلية مؤرشفة" && !it.active })
        val order = s.orders().single()
        assertEquals(3375, order.totalMinor)
        assertEquals("confirmed", order.status)
        // Writing on the phone keeps it valid for the desktop.
        s.transitionOrder(order.id, "delivered")
        x.db.inspectCurrent()
        assertTrue(s.auditEvents().any { it.action == "status" })
    }

    @Test fun backupPruningKeepsManualCopies() {
        val x = seeded()
        val manual = x.db.backup("manual")
        repeat(5) { x.db.backup("after_save") }
        x.db.backups.prune(keep = 2)
        val names = x.db.backups.list().map { it.name }
        assertEquals(2, names.count { it.contains("_after_save_") })
        assertTrue(manual.name in names)
        assertTrue(names.any { it.contains("_initial_") })
    }

    @Test fun exportKeepsExactArabicArchivedRecordsAndSafeCsv() {
        val x = seeded()
        x.s.savePharmacy(PharmacyInput(name = "=HYPERLINK(\"x\")", notes = "سطر\nثانٍ"))
        x.s.saveOrder(x.order(lines = listOf(OrderLineInput(x.a, "1", "10.25"))))
        x.s.archive("pharmacies", x.pharmacy, true)
        val zip = Exports.exportAll(x.db, File(x.db.root, "export.zip"), "test")
        ZipFile(zip).use { z ->
            val names = z.entries().toList().map { it.name }.toSet()
            assertEquals(setOf("all_data.json", "README.txt", "pharmacies.csv", "products.csv", "contacts.csv", "orders.csv",
                "order_items.csv", "audit_events.csv"), names)
            val csv = z.getInputStream(z.getEntry("pharmacies.csv")).readBytes()
            assertEquals(0xEF.toByte(), csv[0])
            val text = String(csv, Charsets.UTF_8)
            assertTrue("\"'=HYPERLINK(\"\"x\"\")\"" in text, text)
            assertTrue(",False," in text)
            @Suppress("UNCHECKED_CAST")
            val json = Json.parse(String(z.getInputStream(z.getEntry("all_data.json")).readBytes(), Charsets.UTF_8)) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val rows = (json["tables"] as Map<String, Any?>)["pharmacies"] as List<Map<String, Any?>>
            assertTrue(rows.any { it["notes"] == "سطر\nثانٍ" })
            assertTrue(rows.any { it["active"] == false })
        }
        assertFailsWith<DataSafetyError> { Exports.exportAll(x.db, zip, "test") }
    }

    @Test fun orderHtmlEscapesUserText() {
        val x = seeded()
        x.s.savePharmacy(PharmacyInput(name = "<script>alert(1)</script>"), x.pharmacy)
        val id = x.s.saveOrder(x.order())
        val html = Exports.orderHtml(x.s.order(id))
        assertFalse("<script>" in html)
        assertTrue("&lt;script&gt;" in html)
    }
}
