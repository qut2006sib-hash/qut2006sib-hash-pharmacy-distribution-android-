package com.localdistribution.pharmacy.platform

import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.localdistribution.pharmacy.core.SqlConnection
import com.localdistribution.pharmacy.core.SqlFactory
import com.localdistribution.pharmacy.core.SqlRow
import java.io.File

/**
 * SqlFactory over the platform SQLite.
 *
 * Data-safety notes:
 *  - Android's default error handler DELETES a database it thinks is corrupt.
 *    We pass a no-op handler: damaged files are reported and preserved.
 *  - Android 9+ silently switches apps to "compatibility WAL" unless WAL is
 *    explicitly disabled. We disable it so the main file is always complete
 *    outside a transaction (backups copy that file) and stays compatible
 *    with the desktop.
 *  - NO_LOCALIZED_COLLATORS stops Android adding its android_metadata table.
 */
class AndroidSqlFactory : SqlFactory {
    private val keepFiles = DatabaseErrorHandler { }

    override fun open(file: File, readOnly: Boolean): SqlConnection {
        if (readOnly && !file.exists()) throw IllegalStateException("missing file")
        val flags = SQLiteDatabase.NO_LOCALIZED_COLLATORS or
            if (readOnly) SQLiteDatabase.OPEN_READONLY else (SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY)
        val db = SQLiteDatabase.openDatabase(file.path, null, flags, keepFiles)
        try {
            if (!readOnly) {
                db.disableWriteAheadLogging()
                db.setForeignKeyConstraintsEnabled(true)
                db.execSQL("PRAGMA synchronous=FULL")
            }
        } catch (t: Throwable) {
            db.close()
            throw t
        }
        return AndroidConnection(db)
    }
}

private class AndroidConnection(private val db: SQLiteDatabase) : SqlConnection {

    private fun bind(st: SQLiteStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, v ->
            val index = i + 1
            when (v) {
                null -> st.bindNull(index)
                is Boolean -> st.bindLong(index, if (v) 1 else 0)
                is Int -> st.bindLong(index, v.toLong())
                is Long -> st.bindLong(index, v)
                else -> st.bindString(index, v.toString())
            }
        }
    }

    private fun queryArgs(args: Array<out Any?>): Array<String> = args.map {
        when (it) {
            null -> throw IllegalArgumentException("NULL query arguments are not supported")
            is Boolean -> if (it) "1" else "0"
            else -> it.toString()
        }
    }.toTypedArray()

    override fun execute(sql: String, vararg args: Any?) {
        if (args.isEmpty()) {
            db.execSQL(sql)
        } else {
            db.compileStatement(sql).use { st -> bind(st, args); st.executeUpdateDelete() }
        }
    }

    override fun insert(sql: String, vararg args: Any?): Long =
        db.compileStatement(sql).use { st -> bind(st, args); st.executeInsert() }

    override fun <T> query(sql: String, vararg args: Any?, mapper: (SqlRow) -> T): List<T> =
        db.rawQuery(sql, queryArgs(args)).use { cursor ->
            val row = CursorRow(cursor)
            val out = ArrayList<T>(cursor.count)
            while (cursor.moveToNext()) out += mapper(row)
            out
        }

    override fun rows(sql: String): List<List<String?>> =
        db.rawQuery(sql, null).use { cursor ->
            val out = mutableListOf<List<String?>>()
            while (cursor.moveToNext()) {
                out += (0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }
            }
            out
        }

    override fun <T> transaction(block: () -> T): T {
        if (db.inTransaction()) return block()
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    override fun close() = db.close()
}

private class CursorRow(private val c: Cursor) : SqlRow {
    override fun string(column: String): String? {
        val i = c.getColumnIndexOrThrow(column)
        return if (c.isNull(i)) null else c.getString(i)
    }

    override fun long(column: String): Long? {
        val i = c.getColumnIndexOrThrow(column)
        return if (c.isNull(i)) null else c.getLong(i)
    }
}
