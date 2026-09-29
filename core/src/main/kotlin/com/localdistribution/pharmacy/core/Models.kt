package com.localdistribution.pharmacy.core

/**
 * Row types. `record()` reproduces the desktop's repositories.record(): the
 * same keys, in the same order, used for audit JSON and exports.
 */
data class Pharmacy(
    val id: Long, val name: String, val phone: String, val contactName: String, val area: String,
    val address: String, val stage: String, val contractStatus: String, val followUpOn: String?,
    val notes: String, val active: Boolean, val createdAt: String, val updatedAt: String,
) {
    fun record(): LinkedHashMap<String, Any?> = linkedMapOf("id" to id, "name" to name, "phone" to phone,
        "contact_name" to contactName, "area" to area, "address" to address, "stage" to stage,
        "contract_status" to contractStatus, "follow_up_on" to followUpOn, "notes" to notes, "active" to active,
        "created_at" to createdAt, "updated_at" to updatedAt)

    companion object {
        fun from(r: SqlRow) = Pharmacy(r.req("id"), r.text("name"), r.text("phone"), r.text("contact_name"), r.text("area"),
            r.text("address"), r.text("stage"), r.text("contract_status"), r.string("follow_up_on"), r.text("notes"),
            r.req("active") != 0L, r.text("created_at"), r.text("updated_at"))
    }
}

data class Product(
    val id: Long, val sku: String, val name: String, val brand: String, val category: String, val size: String,
    val currency: String, val unitPriceMinor: Long, val notes: String, val active: Boolean,
    val createdAt: String, val updatedAt: String,
) {
    fun record(): LinkedHashMap<String, Any?> = linkedMapOf("id" to id, "sku" to sku, "name" to name, "brand" to brand,
        "category" to category, "size" to size, "currency" to currency, "unit_price_minor" to unitPriceMinor,
        "notes" to notes, "active" to active, "created_at" to createdAt, "updated_at" to updatedAt)

    companion object {
        fun from(r: SqlRow) = Product(r.req("id"), r.text("sku"), r.text("name"), r.text("brand"), r.text("category"),
            r.text("size"), r.text("currency"), r.req("unit_price_minor"), r.text("notes"), r.req("active") != 0L,
            r.text("created_at"), r.text("updated_at"))
    }
}

data class Contact(
    val id: Long, val pharmacyId: Long, val occurredAt: String, val kind: String, val note: String,
    val nextFollowUpOn: String?, val createdAt: String, val updatedAt: String,
) {
    fun record(): LinkedHashMap<String, Any?> = linkedMapOf("id" to id, "pharmacy_id" to pharmacyId,
        "occurred_at" to occurredAt, "kind" to kind, "note" to note, "next_follow_up_on" to nextFollowUpOn,
        "created_at" to createdAt, "updated_at" to updatedAt)

    companion object {
        fun from(r: SqlRow) = Contact(r.req("id"), r.req("pharmacy_id"), r.text("occurred_at"), r.text("kind"),
            r.text("note"), r.string("next_follow_up_on"), r.text("created_at"), r.text("updated_at"))
    }
}

data class OrderItem(
    val id: Long, val orderId: Long, val productId: Long, val productName: String, val productSku: String,
    val productBrand: String, val quantity: Long, val unitPriceMinor: Long, val lineTotalMinor: Long,
) {
    fun record(): LinkedHashMap<String, Any?> = linkedMapOf("id" to id, "order_id" to orderId, "product_id" to productId,
        "product_name" to productName, "product_sku" to productSku, "product_brand" to productBrand,
        "quantity" to quantity, "unit_price_minor" to unitPriceMinor, "line_total_minor" to lineTotalMinor)

    companion object {
        fun from(r: SqlRow) = OrderItem(r.req("id"), r.req("order_id"), r.req("product_id"), r.text("product_name"),
            r.text("product_sku"), r.text("product_brand"), r.req("quantity"), r.req("unit_price_minor"), r.req("line_total_minor"))
    }
}

data class Order(
    val id: Long, val pharmacyId: Long, val pharmacyName: String, val pharmacyPhone: String, val pharmacyAddress: String,
    val orderDate: String, val currency: String, val status: String, val subtotalMinor: Long, val discountMinor: Long,
    val totalMinor: Long, val notes: String, val createdAt: String, val updatedAt: String, val items: List<OrderItem>,
) {
    val number: String get() = "ORD-" + id.toString().padStart(6, '0')

    /** The displayed order price is derived from the item rows, never typed. */
    val orderPriceMinor: Long get() = items.sumOf { it.lineTotalMinor }

    fun columns(): LinkedHashMap<String, Any?> = linkedMapOf("id" to id, "pharmacy_id" to pharmacyId,
        "pharmacy_name" to pharmacyName, "pharmacy_phone" to pharmacyPhone, "pharmacy_address" to pharmacyAddress,
        "order_date" to orderDate, "currency" to currency, "status" to status, "subtotal_minor" to subtotalMinor,
        "discount_minor" to discountMinor, "total_minor" to totalMinor, "notes" to notes,
        "created_at" to createdAt, "updated_at" to updatedAt)

    fun record(): LinkedHashMap<String, Any?> = columns().apply {
        put("number", number)
        put("items", items.map { it.record() })
        put("order_price_minor", orderPriceMinor)
    }

    companion object {
        fun from(r: SqlRow, items: List<OrderItem>) = Order(r.req("id"), r.req("pharmacy_id"), r.text("pharmacy_name"),
            r.text("pharmacy_phone"), r.text("pharmacy_address"), r.text("order_date"), r.text("currency"), r.text("status"),
            r.req("subtotal_minor"), r.req("discount_minor"), r.req("total_minor"), r.text("notes"),
            r.text("created_at"), r.text("updated_at"), items)
    }
}

data class AuditEvent(
    val id: Long, val occurredAt: String, val action: String, val entity: String, val entityId: Long?,
    val beforeJson: String, val afterJson: String,
) {
    fun record(): LinkedHashMap<String, Any?> = linkedMapOf("id" to id, "occurred_at" to occurredAt, "action" to action,
        "entity" to entity, "entity_id" to entityId, "before_json" to beforeJson, "after_json" to afterJson)

    companion object {
        fun from(r: SqlRow) = AuditEvent(r.req("id"), r.text("occurred_at"), r.text("action"), r.text("entity"),
            r.long("entity_id"), r.text("before_json"), r.text("after_json"))
    }
}

data class Dashboard(
    val pharmacies: Long, val contracted: Long, val pending: Long, val followups: List<Pharmacy>,
    val totals: List<CurrencyTotal>,
)

data class CurrencyTotal(val currency: String, val status: String, val count: Long, val totalMinor: Long)

/** Input for saving an order line; price is the user-entered text, as on desktop. */
data class OrderLineInput(val productId: Long, val quantity: String, val price: String)
