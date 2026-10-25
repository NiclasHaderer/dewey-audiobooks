package io.dewey.server.database.extensions

import io.dewey.openapi.serializion.kotlin.OpenApiJson
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json

inline fun <reified T : Any> Table.json(
    columnName: String,
    crossinline validation: (T) -> Unit = {},
): Column<T> {
    val serializer = OpenApiJson.serializersModule.serializer<T>()

    return json(columnName, serialize = {
        validation(it)
        OpenApiJson.encodeToString(serializer, it)
    }, deserialize = {
        OpenApiJson.decodeFromString(serializer, it)
    })
}
