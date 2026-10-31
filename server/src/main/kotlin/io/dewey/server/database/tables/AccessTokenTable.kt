package io.dewey.server.database.tables

import io.dewey.models.AccessTokenPermissions
import io.dewey.server.database.extensions.json
import io.dewey.server.database.extensions.timestampMillis
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

// The token itself is a signed JWT naming this row, so revoking it is deleting the row
object AccessTokenTable : UuidTable("access_token") {
    val user = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val name = text("name")
    val permissions = json<AccessTokenPermissions>("permissions")
    val createdAt = timestampMillis("created_at")
    val lastUsedAt = timestampMillis("last_used_at").nullable()

    // Also the expiry of the JWT, which is what enforces it
    val expiresAt = timestampMillis("expires_at").nullable()
}
