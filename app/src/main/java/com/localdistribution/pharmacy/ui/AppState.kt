package com.localdistribution.pharmacy.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.localdistribution.pharmacy.BuildConfig
import com.localdistribution.pharmacy.core.AppDatabase
import com.localdistribution.pharmacy.core.DataSafetyError
import com.localdistribution.pharmacy.core.Service
import com.localdistribution.pharmacy.core.ValidationError
import com.localdistribution.pharmacy.platform.AndroidSqlFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/** Process-wide state: one database, opened once, shared by every screen. */
class AppState(private val context: Context) {
    val factory = AndroidSqlFactory()
    val root = File(context.filesDir, "data")
    val version: String = BuildConfig.VERSION_NAME + "-android"

    var db: AppDatabase? = null
        private set
    var service: Service? by mutableStateOf(null)
        private set
    var starting by mutableStateOf(true)
        private set
    var startupError by mutableStateOf<String?>(null)
        private set

    /** Bumped after every successful write so screens reload. */
    var revision by mutableIntStateOf(0)
        private set

    /** A blocking message dialog (errors, or "saved but backup failed"). */
    var dialog by mutableStateOf<Pair<String, String>?>(null)

    private var lastBackupDay: LocalDate = LocalDate.now()
    private var startRequested = false

    /** Open the database once per process, even if the activity is recreated mid-start. */
    suspend fun startOnce() {
        if (startRequested) return
        startRequested = true
        start()
    }

    suspend fun start() {
        starting = true
        val (opened, error) = withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase(root, factory)
                Service(database).seedFieldData()
                database to null
            } catch (e: Exception) {
                null to describe(e)
            }
        }
        db?.close()
        db = opened
        service = opened?.let { Service(it) }
        startupError = error
        lastBackupDay = LocalDate.now()
        starting = false
        revision++
    }

    /** Desktop takes a "daily" copy once the date changes; here it is checked when the app resumes. */
    suspend fun dailyBackupIfNeeded() {
        val database = db ?: return
        if (LocalDate.now() > lastBackupDay) {
            withContext(Dispatchers.IO) { runCatching { database.backup("daily"); database.backups.prune() } }
            lastBackupDay = LocalDate.now()
            revision++
        }
    }

    /**
     * Run a write on a background thread. Validation errors are shown as-is;
     * a committed write whose backup failed is reported as saved, never as lost.
     */
    suspend fun <T> write(block: (Service) -> T): T? {
        val svc = service ?: return null
        val result = withContext(Dispatchers.IO) {
            try {
                Result.success(block(svc))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        return result.fold(
            onSuccess = {
                revision++
                val backupError = db?.backups?.lastError.orEmpty()
                if (backupError.isNotEmpty()) {
                    dialog = "تم الحفظ — النسخ الاحتياطي يحتاج معالجة" to
                        "حُفظت البيانات في قاعدة البيانات، لكن إنشاء النسخة الاحتياطية لم ينجح.\n$backupError"
                }
                it
            },
            onFailure = {
                dialog = "لم تكتمل العملية" to describe(it)
                null
            },
        )
    }

    /** Background work that is not a data write (exports, verification, restore). */
    suspend fun <T> task(title: String = "لم تكتمل العملية", block: () -> T): T? {
        val result = withContext(Dispatchers.IO) {
            try { Result.success(block()) } catch (e: Exception) { Result.failure(e) }
        }
        revision++
        return result.getOrElse { dialog = title to describe(it); null }
    }

    fun describe(e: Throwable): String = when (e) {
        is ValidationError, is DataSafetyError -> e.message.orEmpty()
        else -> "تعذّر إتمام العملية. البيانات السابقة محفوظة.\n" + (e.message ?: e.toString())
    }

    // ---------- files shared through the Storage Access Framework ----------

    fun scratch(name: String): File {
        val dir = File(context.cacheDir, "transfer/" + UUID.randomUUID().toString())
        dir.mkdirs()
        return File(dir, name)
    }

    fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name.replace('/', '_')
            }
        }
        return "backup.sqlite3"
    }

    /** Copy a picked document into app storage so SQLite can open it. */
    fun importToScratch(uri: Uri): File {
        val target = scratch(displayName(uri))
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "تعذّر قراءة الملف المختار." }
            target.outputStream().use { output -> input.copyTo(output); output.fd.sync() }
        }
        return target
    }

    /** Write a finished file to a user-chosen location, then read it back and compare. */
    fun exportTo(file: File, uri: Uri) {
        val expected = sha256(file.readBytes())
        val resolver = context.contentResolver
        (runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull() ?: resolver.openOutputStream(uri, "w")).use { output ->
            requireNotNull(output) { "تعذّر الكتابة في الموقع المختار." }
            file.inputStream().use { it.copyTo(output) }
            output.flush()
        }
        val written = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "تعذّر التحقق من الملف المحفوظ." }
            sha256(input.readBytes())
        }
        if (!written.contentEquals(expected)) throw DataSafetyError("الملف المحفوظ لا يطابق النسخة الأصلية. أعد المحاولة في موقع آخر.")
    }

    fun cleanScratch() {
        File(context.cacheDir, "transfer").deleteRecursively()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
