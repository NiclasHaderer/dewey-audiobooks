package io.dewey.server.database.tables

import io.dewey.auth.models.DeweyDatabaseUser
import io.dewey.models.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.Uuid

object UserTable : UuidTable("user") {
    val username = varchar("username", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val admin = bool("admin").default(false)
    val tokenVersion = integer("token_version")
}

data class UserRow(
    val id: Uuid,
    val username: String,
    val passwordHash: String,
    val admin: Boolean,
    val tokenVersion: Int,
) {
    fun toModel(): User = User(id = id, username = username, admin = admin)

    fun toExternalUser(): DeweyDatabaseUser =
        DeweyDatabaseUser(
            id = id,
            username = username,
            passwordHash = passwordHash,
            tokenVersion = tokenVersion,
        )
}

fun ResultRow.toUserRow(): UserRow =
    UserRow(
        id = this[UserTable.id].value,
        username = this[UserTable.username],
        passwordHash = this[UserTable.passwordHash],
        admin = this[UserTable.admin],
        tokenVersion = this[UserTable.tokenVersion],
    )

context(_: Transaction)
fun UserTable.insert(row: UserRow): Uuid {
    insert {
        it[id] = row.id
        it[username] = row.username
        it[passwordHash] = row.passwordHash
        it[admin] = row.admin
        it[tokenVersion] = row.tokenVersion
    }
    return row.id
}
