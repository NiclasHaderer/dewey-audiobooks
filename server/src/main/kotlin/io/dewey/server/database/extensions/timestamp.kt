package io.dewey.server.database.extensions

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import kotlin.time.Instant

// SQLite has no timestamp type, and Exposed's timestamp() falls back to text formatted in the
// JVM default zone, so rows written by JVMs in different zones stop being comparable.
class InstantMillisColumnType : ColumnType<Instant>() {
    override fun sqlType(): String = "BIGINT"

    override fun valueFromDB(value: Any): Instant =
        when (value) {
            is Number -> Instant.fromEpochMilliseconds(value.toLong())
            else -> error("Unexpected value for timestamp column: $value (${value::class})")
        }

    override fun notNullValueToDB(value: Instant): Any = value.toEpochMilliseconds()
}

fun Table.timestampMillis(name: String): Column<Instant> = registerColumn(name, InstantMillisColumnType())
