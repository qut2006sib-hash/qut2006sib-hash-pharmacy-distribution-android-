package com.localdistribution.pharmacy.core

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/** JVM SqlFactory over xerial sqlite-jdbc, used to test the core against real SQLite. */
class JdbcFactory : SqlFactory {
    override fun open(file: File, readOnly: Boolean): SqlConnection {
        if (readOnly && !file.exists()) throw IllegalStateException("missing file")
        val config = SQLiteConfig()
        config.setReadOnly(readOnly)
        config.enforceForeignKeys(!readOnly)
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL)
        return JdbcConnection(config.createConnection("jdbc:sqlite:" + file.absolutePath))
    }
}

class JdbcConnection(private val c: Connection) : SqlConnection {
    private fun bind(st: PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, v ->
            when (v) {
                null -> st.setNull(i + 1, Types.NULL)
                is Boolean -> st.setLong(i + 1, if (v) 1 else 0)
                is Int -> st.setLong(i + 1, v.toLong())
                is Long -> st.setLong(i + 1, v)
                else -> st.setString(i + 1, v.toString())
            }
        }
    }

    override fun execute(sql: String, vararg args: Any?) {
        c.prepareStatement(sql).use { bind(it, args); it.execute() }
    }

    override fun insert(sql: String, vararg args: Any?): Long {
        c.prepareStatement(sql).use { bind(it, args); it.executeUpdate() }
        return c.createStatement().use { st -> st.executeQuery("SELECT last_insert_rowid()").use { it.next(); it.getLong(1) } }
    }

    override fun <T> query(sql: String, vararg args: Any?, mapper: (SqlRow) -> T): List<T> =
        c.prepareStatement(sql).use { st ->
            bind(st, args)
            st.executeQuery().use { rs ->
                val row = object : SqlRow {
                    override fun string(column: String): String? = rs.getString(column)
                    override fun long(column: String): Long? = rs.getLong(column).takeUnless { rs.wasNull() }
                }
                val out = mutableListOf<T>()
                while (rs.next()) out += mapper(row)
                out
            }
        }

    override fun rows(sql: String): List<List<String?>> = c.createStatement().use { st ->
        val hasResult = st.execute(sql)
        if (!hasResult) return emptyList()
        st.resultSet.use { rs: ResultSet ->
            val n = rs.metaData.columnCount
            val out = mutableListOf<List<String?>>()
            while (rs.next()) out += (1..n).map { rs.getString(it) }
            out
        }
    }

    override fun <T> transaction(block: () -> T): T {
        if (!c.autoCommit) return block()
        c.autoCommit = false
        try {
            val result = block()
            c.commit()
            return result
        } catch (t: Throwable) {
            c.rollback()
            throw t
        } finally {
            c.autoCommit = true
        }
    }

    override fun close() = c.close()
}
