package com.localdistribution.pharmacy.core

import java.io.Closeable
import java.io.File

/** One result row, read by column name. */
interface SqlRow {
    fun string(column: String): String?
    fun long(column: String): Long?
    fun text(column: String): String = string(column) ?: ""
    fun req(column: String): Long = long(column) ?: error("NULL in $column")
}

/**
 * The minimal SQLite surface the core needs. Android wraps SQLiteDatabase,
 * tests wrap JDBC. Arguments may be String, Long, Int, Boolean or null.
 * Query arguments must not be null (Android binds query arguments as text).
 */
interface SqlConnection : Closeable {
    fun execute(sql: String, vararg args: Any?)
    fun insert(sql: String, vararg args: Any?): Long
    fun <T> query(sql: String, vararg args: Any?, mapper: (SqlRow) -> T): List<T>

    /** Positional rows as strings; used for PRAGMAs whose column names vary. */
    fun rows(sql: String): List<List<String?>>
    fun <T> transaction(block: () -> T): T

    fun <T> first(sql: String, vararg args: Any?, mapper: (SqlRow) -> T): T? =
        query(sql, *args, mapper = mapper).firstOrNull()
}

/** Opens SQLite files. Read-only handles are used for validation only. */
fun interface SqlFactory {
    fun open(file: File, readOnly: Boolean): SqlConnection
}
