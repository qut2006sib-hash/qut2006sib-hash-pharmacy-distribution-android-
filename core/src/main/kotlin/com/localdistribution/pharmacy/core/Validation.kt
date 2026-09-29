package com.localdistribution.pharmacy.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A user-facing validation failure; the message is shown as-is (Arabic). */
class ValidationError(message: String) : IllegalArgumentException(message)

object Labels {
    val STAGES = linkedMapOf("new" to "لم يتم التواصل", "contacted" to "تم التواصل", "interested" to "مهتمة",
        "later" to "متابعة لاحقة", "declined" to "غير مهتمة")
    val CONTRACTS = linkedMapOf("none" to "غير متعاقدة", "active" to "متعاقدة", "paused" to "التعاقد متوقف")
    val KINDS = linkedMapOf("visit" to "زيارة", "call" to "اتصال", "whatsapp" to "واتساب", "other" to "أخرى")
    val ORDER_STATES = linkedMapOf("draft" to "مسودة", "confirmed" to "مؤكدة", "delivered" to "مسلّمة", "cancelled" to "ملغاة")
    val CURRENCIES = linkedMapOf("SYP" to "ليرة سورية — SYP", "USD" to "دولار — USD")
    val AUDIT_ACTIONS = mapOf("create" to "إضافة", "update" to "تعديل", "archive" to "أرشفة", "unarchive" to "إعادة تنشيط",
        "follow_up" to "تحديث المتابعة", "status" to "تغيير حالة", "restore" to "استعادة نسخة",
        "initial_pharmacy_import" to "إعداد قائمة الصيدليات الأولية")
    val AUDIT_ENTITIES = mapOf("pharmacies" to "صيدلية", "products" to "منتج", "orders" to "طلبية", "contacts" to "تواصل",
        "database" to "قاعدة البيانات", "system" to "إعداد النظام")
}

const val MAX_MONEY = 100_000_000_000_000L

object Clock {
    private val seconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
    private val micros = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx")
    private val stampFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'_'SSSSSS'Z'")

    /** Same text as Python `datetime.now(timezone.utc).isoformat(timespec="seconds")`. */
    fun now(): String = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(seconds)
    fun nowMicros(): String = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS).format(micros)
    fun stamp(): String = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS).format(stampFormat)
    fun today(): String = LocalDate.now().toString()
    fun nowLocalMinutes(): String = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
}

object Validate {
    /** Parse a user-entered decimal amount into hundredths, never floating point. */
    fun money(value: Any?): Long {
        val error = ValidationError("أدخل مبلغًا موجبًا أو صفرًا، وبحد أقصى منزلتان عشريتان، دون فواصل آلاف.")
        return try {
            val amount = BigDecimal(value.toString().trim().replace("٫", "."))
            if (amount.signum() < 0 || amount > BigDecimal(MAX_MONEY).movePointLeft(2)) throw error
            val scaled = amount.movePointRight(2)
            if (scaled.stripTrailingZeros().scale() > 0) throw error
            scaled.longValueExact()
        } catch (e: ValidationError) {
            throw e
        } catch (e: RuntimeException) {
            throw error
        }
    }

    fun moneyText(minor: Long): String = "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

    fun text(value: String?, label: String, limit: Int = 200, required: Boolean = false): String {
        val cleaned = (value ?: "").trim()
        if (required && cleaned.isEmpty()) throw ValidationError("الحقل مطلوب: $label")
        if (cleaned.codePointCount(0, cleaned.length) > limit) throw ValidationError("$label: الحد الأقصى $limit حرفًا.")
        return cleaned
    }

    fun isoDate(value: String?, optional: Boolean = false): String? {
        val cleaned = (value ?: "").trim()
        if (cleaned.isEmpty() && optional) return null
        return try {
            val parsed = LocalDate.parse(cleaned)
            if (parsed.toString() != cleaned) throw IllegalArgumentException()
            cleaned
        } catch (e: RuntimeException) {
            throw ValidationError("أدخل التاريخ بالشكل YYYY-MM-DD، مثل 2026-09-25.")
        }
    }

    /** Mirrors `datetime.fromisoformat(value).isoformat(timespec="minutes")` for local times. */
    fun isoDateTime(value: String?): String {
        val cleaned = (value ?: "").trim().replace(' ', 'T')
        val minutes = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        return try {
            val parsed = if (cleaned.length == 10) LocalDate.parse(cleaned).atStartOfDay() else LocalDateTime.parse(cleaned)
            parsed.format(minutes)
        } catch (e: RuntimeException) {
            try {
                val parsed = OffsetDateTime.parse(cleaned)
                parsed.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmxxx"))
            } catch (e2: RuntimeException) {
                throw ValidationError("أدخل تاريخ ووقت التواصل بالشكل YYYY-MM-DD HH:MM.")
            }
        }
    }

    fun choice(value: String?, options: Map<String, String>): String {
        if (value == null || value !in options) throw ValidationError("القيمة المحددة غير مسموحة.")
        return value
    }

    fun quantity(value: Any?): Long {
        return try {
            val n = value.toString().trim().toLong()
            if (n !in 1..1_000_000) throw IllegalArgumentException()
            n
        } catch (e: RuntimeException) {
            throw ValidationError("الكمية عدد صحيح بين 1 و1000000.")
        }
    }
}
