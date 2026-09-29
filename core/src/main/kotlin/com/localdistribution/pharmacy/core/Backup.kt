package com.localdistribution.pharmacy.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

/** A database file failed validation; the message is shown to the user. */
class DataSafetyError(message: String) : IllegalStateException(message)

data class InspectResult(val revision: String, val pharmacies: Long, val orders: Long)

/** Port of backup.inspect_database: refuse anything that is not a healthy database of this app. */
object Inspector {
    fun inspect(path: File, factory: SqlFactory, full: Boolean = true): InspectResult {
        if (!path.isFile || path.length() == 0L) throw DataSafetyError("ملف قاعدة البيانات غير موجود أو فارغ.")
        val conn = try {
            factory.open(path, readOnly = true)
        } catch (e: Exception) {
            throw DataSafetyError("الملف ليس قاعدة بيانات لهذا التطبيق.")
        }
        conn.use { c ->
            try {
                return check(c, full)
            } catch (e: DataSafetyError) {
                throw e
            } catch (e: Exception) {
                throw DataSafetyError("الملف ليس قاعدة بيانات لهذا التطبيق أو أنه تالف.")
            }
        }
    }

    private fun check(c: SqlConnection, full: Boolean): InspectResult {
        if (c.rows("PRAGMA application_id").firstOrNull()?.firstOrNull()?.toLongOrNull() != Schema.APP_ID.toLong()) {
            throw DataSafetyError("الملف ليس قاعدة بيانات لهذا التطبيق.")
        }
        val checks = c.rows(if (full) "PRAGMA integrity_check" else "PRAGMA quick_check")
        if (checks != listOf(listOf("ok"))) throw DataSafetyError("فحص سلامة قاعدة البيانات لم ينجح؛ لن يتم استبدال البيانات.")
        if (c.rows("PRAGMA foreign_key_check").isNotEmpty()) throw DataSafetyError("توجد علاقات غير سليمة في قاعدة البيانات.")
        // Refuse views/triggers in place of the expected plain application schema.
        val objects = c.query("SELECT name, type FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'") { it.text("name") to it.text("type") }
        val tables = objects.filter { it.second == "table" }.map { it.first }.toSet()
        if (!tables.containsAll(Schema.REQUIRED) || objects.any { it.second == "trigger" || it.second == "view" }) {
            throw DataSafetyError("بنية قاعدة البيانات غير متوافقة مع التطبيق.")
        }
        for ((table, columns) in Schema.COLUMNS) {
            val actual = c.rows("PRAGMA table_info(\"$table\")").map { it[1] }.toSet()
            if (actual != columns.toSet()) throw DataSafetyError("حقول قاعدة البيانات غير متوافقة مع هذا الإصدار.")
        }
        val revision = c.query("SELECT version_num FROM alembic_version") { it.text("version_num") }
        if (revision != listOf(Schema.CURRENT_REVISION)) {
            throw DataSafetyError("إصدار قاعدة البيانات غير مدعوم؛ استخدم إصدار البرنامج الموافق لها.")
        }
        if (c.first("SELECT family FROM app_meta WHERE id=1") { it.string("family") } != Schema.FAMILY) {
            throw DataSafetyError("هوية قاعدة البيانات غير صحيحة.")
        }
        // Cross-table business invariant that SQLite CHECKs cannot express.
        val broken = c.first("""SELECT o.id AS id FROM orders o LEFT JOIN order_items i ON i.order_id=o.id
            GROUP BY o.id HAVING COUNT(i.id)=0 OR COALESCE(SUM(i.line_total_minor),0)<>o.subtotal_minor""") { it.long("id") }
        if (broken != null) throw DataSafetyError("إجماليات بعض الطلبيات لا تطابق تفاصيلها.")
        return InspectResult(revision[0],
            c.first("SELECT COUNT(*) AS n FROM pharmacies") { it.req("n") } ?: 0,
            c.first("SELECT COUNT(*) AS n FROM orders") { it.req("n") } ?: 0)
    }
}

/**
 * File helpers using only java.io, so the core runs on Android 5+ (java.nio.file
 * only exists from Android 8). On Linux/Android, File.renameTo is rename(2):
 * atomic, and it replaces an existing target.
 */
object Files2 {
    fun syncFile(file: File) {
        RandomAccessFile(file, "rw").use { it.fd.sync() }
    }

    /** Best effort: directory fsync needs java.nio.file, which Android < 8 lacks. */
    fun syncDir(dir: File) {
        try {
            java.nio.channels.FileChannel.open(dir.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
        } catch (t: Throwable) {
        }
    }

    /** Atomically move `from` over `to` (same directory tree, same filesystem). */
    fun replace(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("تعذّر نقل الملف إلى مكانه النهائي: " + to.name)
    }

    /** Publish `temp` as `destination`, never replacing an existing file. */
    fun publishExclusive(temp: File, destination: File) {
        // createNewFile is atomic: it fails if the name is already taken.
        if (!destination.createNewFile()) throw DataSafetyError("الملف موجود؛ اختر اسمًا جديدًا للحفاظ عليه.")
        try {
            replace(temp, destination)
            syncDir(destination.absoluteFile.parentFile)
        } catch (e: Exception) {
            if (destination.exists() && destination.length() == 0L) destination.delete()
            throw e
        }
    }
}

/**
 * Write a complete snapshot, validate it, fsync, then publish atomically.
 * The caller must guarantee no write transaction is running on `source`
 * (AppDatabase holds its lock). The app never uses WAL, so the main file is
 * complete whenever no transaction is open.
 */
fun snapshot(source: File, destination: File, factory: SqlFactory): File {
    val src = source.canonicalFile
    val dst = destination.absoluteFile
    if (src == dst.canonicalFile) throw DataSafetyError("اختر ملفًا مختلفًا عن قاعدة البيانات الحالية.")
    if (dst.exists()) throw DataSafetyError("ملف النسخة موجود؛ اختر اسمًا جديدًا للحفاظ عليه.")
    dst.parentFile.mkdirs()
    val temp = File(dst.parentFile, ".${dst.name}.${UUID.randomUUID().toString().replace("-", "")}.tmp")
    try {
        src.inputStream().use { input -> FileOutputStream(temp).use { output -> input.copyTo(output); output.fd.sync() } }
        // A copied WAL-mode file would need its -wal companion; store every snapshot in rollback mode.
        factory.open(temp, readOnly = false).use { it.rows("PRAGMA journal_mode=DELETE") }
        Inspector.inspect(temp, factory)
        temp.setReadable(false, false); temp.setReadable(true, true)
        temp.setWritable(false, false); temp.setWritable(true, true)
        Files2.syncFile(temp)
        Files2.publishExclusive(temp, dst)
        return dst
    } finally {
        if (temp.exists()) temp.delete()
        for (suffix in listOf("-journal", "-wal", "-shm")) File(temp.path + suffix).delete()
    }
}

class BackupManager(private val root: File, private val factory: SqlFactory) {
    val database = File(root, "app.sqlite3")
    val directory = File(root, "backups")

    @Volatile var lastError: String = ""
        private set
    @Volatile var lastSuccess: File? = null
        private set

    init {
        directory.mkdirs()
    }

    fun create(reason: String = "manual", destination: File? = null): File {
        val path = destination ?: File(directory, "${Clock.stamp()}_${reason}_${UUID.randomUUID().toString().replace("-", "").take(6)}.sqlite3")
        return try {
            snapshot(database, path, factory).also { lastSuccess = it; lastError = "" }
        } catch (e: Exception) {
            lastError = e.message ?: e.toString()
            throw e
        }
    }

    /** The transaction already committed; a failed copy is reported, never treated as unsaved. */
    fun afterSave() {
        try {
            create("after_save")
            prune()
        } catch (e: Exception) {
        }
    }

    /**
     * Phone storage is small, so automatic snapshots are capped per reason.
     * Manual, initial, before_restore and external backups are never deleted.
     */
    fun prune(keep: Int = AUTO_KEEP) {
        for (reason in AUTO_REASONS) {
            list().filter { it.name.contains("_${reason}_") }.drop(keep).forEach { it.delete() }
        }
    }

    fun list(): List<File> = (directory.listFiles { f -> f.isFile && f.name.endsWith(".sqlite3") && !f.name.startsWith(".") }
        ?: emptyArray()).sortedByDescending { it.name }

    /**
     * Caller must have closed every connection to the live database.
     * Returns what now holds the previous data: a validated backup, or a
     * recovery folder with forensic copies if the current file was damaged.
     */
    fun restoreClosed(source: File): File? {
        if (source.canonicalFile == database.canonicalFile) throw DataSafetyError("لا يمكن استعادة قاعدة البيانات فوق نفسها.")
        val stage = File(root, ".restore-${UUID.randomUUID().toString().replace("-", "")}.sqlite3")
        snapshot(source, stage, factory) // Validate a consistent independent copy first.
        try {
            var preserved: File? = null
            if (database.exists()) {
                val healthy = try { Inspector.inspect(database, factory); true } catch (e: Exception) { false }
                if (!healthy) {
                    preserved = File(File(root, "recovery"), Clock.stamp())
                    preserved.mkdirs()
                    for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                        val original = File(database.path + suffix)
                        if (original.exists()) {
                            val target = File(preserved, original.name)
                            original.copyTo(target)
                            Files2.syncFile(target)
                        }
                    }
                    Files2.syncDir(preserved)
                } else {
                    preserved = create("before_restore")
                }
            }
            for (suffix in listOf("-wal", "-shm", "-journal")) File(database.path + suffix).delete()
            Files2.replace(stage, database)
            database.setWritable(true, true)
            Files2.syncDir(root)
            lastError = ""
            return preserved
        } finally {
            if (stage.exists()) stage.delete()
        }
    }

    companion object {
        const val AUTO_KEEP = 60
        val AUTO_REASONS = listOf("after_save", "startup", "daily")
    }
}
