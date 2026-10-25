package io.dewey.server.database.tables

import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object ImageTable : UuidTable("image") {
    val image = blob("image")
}
