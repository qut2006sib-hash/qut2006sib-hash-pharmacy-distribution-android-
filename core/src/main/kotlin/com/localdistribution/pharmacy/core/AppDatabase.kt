package com.localdistribution.pharmacy.core

import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Port of database.Database. All access is serialised through one lock, so a
 * snapshot copy never races a write. Opening an existing file first validates
 * it and takes a "startup" backup; a failed check stops startup and leaves the
 * data untouched.
 */
class AppDatabase(val root: File, private val factory: SqlFactory) : Closeable {
    val path = File(root, "app.sqlite3")
    val backups: BackupManager
    private val lock = ReentrantLock()
    private var conn: SqlConnection? = null

    init {
        root.mkdirs()
        root.setReadable(false, false); root.setReadable(true, true)
        root.setWritable(false, false); root.setWritable(true, true)
        root.setExecutable(false, false); root.setExecutable(true, true)
        backups = BackupManager(root, factory)
        lock.withLock { openLocked() }
    }

    private fun openLocked() {
        val existed = path.exists()
        if (existed) {
            Inspector.inspect(path, factory)
            // A failed safety copy stops startup; existing data stays untouched.
            backups.create("startup")
        }
        val c = factory.open(path, readOnly = false)
        try {
            c.execute("PRAGMA application_id=${Schema.APP_ID}")
            Schema.upgrade(c)
            conn = c
            Inspector.inspect(path, factory)
            if (!existed) backups.create("initial")
            Service(this).seedInitialPharmacies()
            Inspector.inspect(path, factory)
        } catch (t: Throwable) {
            conn = null
            c.close()
            throw t
        }
    }

    private fun current(): SqlConnection = conn ?: throw DataSafetyError("قاعدة البيانات مغلقة. أعد فتح التطبيق.")

    fun <T> read(block: (SqlConnection) -> T): T = lock.withLock { block(current()) }

    /** One transaction; after commit a validated snapshot is attempted (failure is reported, not fatal). */
    fun <T> write(block: (SqlConnection) -> T): T = lock.withLock {
        val c = current()
        val result = c.transaction { block(c) }
        backups.afterSave()
        result
    }

    /** Run with the database lock held and no transaction open (for backups/exports). */
    fun <T> exclusive(block: () -> T): T = lock.withLock { block() }

    fun backup(reason: String, destination: File? = null): File = lock.withLock { backups.create(reason, destination) }

    fun inspectCurrent(): InspectResult = lock.withLock { Inspector.inspect(path, factory) }

    /**
     * Replace the data with a validated backup. Current data is preserved first
     * (as a before_restore backup, or forensic copies if it was damaged).
     */
    fun restore(source: File): File? = lock.withLock {
        Inspector.inspect(source, factory)
        conn?.close()
        conn = null
        val preserved = try {
            backups.restoreClosed(source)
        } catch (e: Exception) {
            openLocked()
            throw e
        }
        openLocked()
        Service(this).recordRestore(source.name, preserved?.path)
        preserved
    }

    override fun close() {
        lock.withLock {
            conn?.close()
            conn = null
        }
    }

    companion object {
        /**
         * Restore without a working live database (startup failed). Mirrors the
         * desktop's startup "restore a healthy copy" path.
         */
        fun restoreWithoutOpening(root: File, factory: SqlFactory, source: File): File? =
            BackupManager(root, factory).restoreClosed(source)
    }
}
