package io.dewey.server.plugins.auth

import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.UserTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

// Will not be called concurrently
internal fun requireAnotherAdminExists(userId: Uuid) {
    val others =
        UserTable
            .selectAll()
            .where { (UserTable.admin eq true) and (UserTable.id neq userId) }
            .count()

    if (others == 0L) throw ErrorResponse.userError("Cannot remove the only admin user.")
}
