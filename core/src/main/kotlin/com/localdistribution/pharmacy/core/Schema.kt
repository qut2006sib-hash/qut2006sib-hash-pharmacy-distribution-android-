package com.localdistribution.pharmacy.core

/**
 * Frozen copy of the desktop schema (alembic revision 0001_initial), taken
 * from a database created by the desktop application itself. Keeping it
 * identical is what makes backups portable between desktop and phone.
 */
object Schema {
    const val APP_ID = 0x50484453
    const val CURRENT_REVISION = "0001_initial"
    const val FAMILY = "pharmacy_distribution"

    val REQUIRED = setOf("app_meta", "pharmacies", "products", "contacts", "orders", "order_items", "audit_events", "alembic_version")

    /** Columns of every application table, in desktop order. */
    val COLUMNS: Map<String, List<String>> = linkedMapOf(
        "app_meta" to listOf("id", "family", "created_at"),
        "pharmacies" to listOf("id", "name", "phone", "contact_name", "area", "address", "stage", "contract_status",
            "follow_up_on", "notes", "active", "created_at", "updated_at"),
        "products" to listOf("id", "sku", "name", "brand", "category", "size", "currency", "unit_price_minor", "notes",
            "active", "created_at", "updated_at"),
        "contacts" to listOf("id", "pharmacy_id", "occurred_at", "kind", "note", "next_follow_up_on", "created_at", "updated_at"),
        "orders" to listOf("id", "pharmacy_id", "pharmacy_name", "pharmacy_phone", "pharmacy_address", "order_date", "currency",
            "status", "subtotal_minor", "discount_minor", "total_minor", "notes", "created_at", "updated_at"),
        "order_items" to listOf("id", "order_id", "product_id", "product_name", "product_sku", "product_brand", "quantity",
            "unit_price_minor", "line_total_minor"),
        "audit_events" to listOf("id", "occurred_at", "action", "entity", "entity_id", "before_json", "after_json"),
    )

    val DDL = listOf(
        """CREATE TABLE alembic_version (
	version_num VARCHAR(32) NOT NULL,
	CONSTRAINT alembic_version_pkc PRIMARY KEY (version_num)
)""",
        """CREATE TABLE app_meta (
	id INTEGER NOT NULL,
	family VARCHAR NOT NULL,
	created_at VARCHAR NOT NULL,
	PRIMARY KEY (id)
)""",
        """CREATE TABLE pharmacies (
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	name VARCHAR(200) NOT NULL,
	phone VARCHAR(100) NOT NULL,
	contact_name VARCHAR(200) NOT NULL,
	area VARCHAR(200) NOT NULL,
	address TEXT NOT NULL,
	stage VARCHAR NOT NULL,
	contract_status VARCHAR NOT NULL,
	follow_up_on VARCHAR,
	notes TEXT NOT NULL,
	active BOOLEAN NOT NULL,
	created_at VARCHAR NOT NULL,
	updated_at VARCHAR NOT NULL,
	CHECK (stage IN ('new','contacted','interested','later','declined')),
	CHECK (contract_status IN ('none','active','paused'))
)""",
        """CREATE TABLE products (
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	sku VARCHAR(100) NOT NULL,
	name VARCHAR(200) NOT NULL,
	brand VARCHAR(100) NOT NULL,
	category VARCHAR(100) NOT NULL,
	size VARCHAR(100) NOT NULL,
	currency VARCHAR(3) NOT NULL,
	unit_price_minor INTEGER NOT NULL,
	notes TEXT NOT NULL,
	active BOOLEAN NOT NULL,
	created_at VARCHAR NOT NULL,
	updated_at VARCHAR NOT NULL,
	CHECK (unit_price_minor >= 0 AND unit_price_minor <= 100000000000000),
	CHECK (currency IN ('SYP','USD')),
	UNIQUE (sku)
)""",
        """CREATE TABLE contacts (
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	pharmacy_id INTEGER NOT NULL,
	occurred_at VARCHAR NOT NULL,
	kind VARCHAR NOT NULL,
	note TEXT NOT NULL,
	next_follow_up_on VARCHAR,
	created_at VARCHAR NOT NULL,
	updated_at VARCHAR NOT NULL,
	CHECK (kind IN ('visit','call','whatsapp','other')),
	FOREIGN KEY(pharmacy_id) REFERENCES pharmacies (id) ON DELETE RESTRICT
)""",
        """CREATE TABLE orders (
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	pharmacy_id INTEGER NOT NULL,
	pharmacy_name VARCHAR NOT NULL,
	pharmacy_phone VARCHAR NOT NULL,
	pharmacy_address TEXT NOT NULL,
	order_date VARCHAR NOT NULL,
	currency VARCHAR(3) NOT NULL,
	status VARCHAR NOT NULL,
	subtotal_minor INTEGER NOT NULL,
	discount_minor INTEGER NOT NULL,
	total_minor INTEGER NOT NULL,
	notes TEXT NOT NULL,
	created_at VARCHAR NOT NULL,
	updated_at VARCHAR NOT NULL,
	CHECK (status IN ('draft','confirmed','delivered','cancelled')),
	CHECK (currency IN ('SYP','USD')),
	CHECK (subtotal_minor >= 0 AND subtotal_minor <= 100000000000000),
	CHECK (discount_minor >= 0 AND discount_minor <= subtotal_minor),
	CHECK (total_minor = subtotal_minor - discount_minor),
	FOREIGN KEY(pharmacy_id) REFERENCES pharmacies (id) ON DELETE RESTRICT
)""",
        """CREATE TABLE order_items (
	id INTEGER NOT NULL,
	order_id INTEGER NOT NULL,
	product_id INTEGER NOT NULL,
	product_name VARCHAR NOT NULL,
	product_sku VARCHAR NOT NULL,
	product_brand VARCHAR NOT NULL,
	quantity INTEGER NOT NULL,
	unit_price_minor INTEGER NOT NULL,
	line_total_minor INTEGER NOT NULL,
	PRIMARY KEY (id),
	CHECK (quantity > 0 AND quantity <= 1000000),
	CHECK (unit_price_minor >= 0),
	CHECK (line_total_minor = quantity * unit_price_minor),
	FOREIGN KEY(order_id) REFERENCES orders (id) ON DELETE RESTRICT,
	FOREIGN KEY(product_id) REFERENCES products (id) ON DELETE RESTRICT
)""",
        """CREATE TABLE audit_events (
	id INTEGER NOT NULL,
	occurred_at VARCHAR NOT NULL,
	action VARCHAR NOT NULL,
	entity VARCHAR NOT NULL,
	entity_id INTEGER,
	before_json TEXT NOT NULL,
	after_json TEXT NOT NULL,
	PRIMARY KEY (id)
)""",
        "CREATE INDEX ix_pharmacies_name ON pharmacies (name)",
        "CREATE INDEX ix_pharmacies_follow_up_on ON pharmacies (follow_up_on)",
        "CREATE INDEX ix_contacts_pharmacy_id_occurred_at ON contacts (pharmacy_id, occurred_at)",
        "CREATE INDEX ix_orders_pharmacy_id_order_date ON orders (pharmacy_id, order_date)",
        "CREATE INDEX ix_order_items_order_id ON order_items (order_id)",
        "CREATE INDEX ix_order_items_product_id ON order_items (product_id)",
        "CREATE INDEX ix_audit_events_entity_entity_id ON audit_events (entity, entity_id)",
    )

    /** Equivalent of `alembic upgrade head` for revision 0001. Idempotent. */
    fun upgrade(conn: SqlConnection) {
        val hasVersion = conn.first("SELECT name FROM sqlite_master WHERE type='table' AND name='alembic_version'") { it.string("name") } != null
        if (hasVersion) {
            val version = conn.query("SELECT version_num FROM alembic_version") { it.string("version_num") }
            if (version != listOf(CURRENT_REVISION)) {
                throw IllegalStateException("إصدار قاعدة البيانات غير مدعوم؛ استخدم إصدار البرنامج الموافق لها.")
            }
            return
        }
        conn.transaction {
            for (statement in DDL) conn.execute(statement)
            // Desktop writes Python's isoformat() with microseconds here.
            conn.execute("INSERT INTO app_meta VALUES (1, ?, ?)", FAMILY, Clock.nowMicros())
            conn.execute("INSERT INTO alembic_version (version_num) VALUES (?)", CURRENT_REVISION)
        }
    }
}
