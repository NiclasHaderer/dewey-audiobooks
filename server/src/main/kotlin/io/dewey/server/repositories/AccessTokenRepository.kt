package io.dewey.server.repositories

import io.dewey.models.AccessToken
import io.dewey.models.AccessTokenPermissions
import io.dewey.models.CreateAccessToken
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.tables.AccessTokenTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

interface AccessTokenRepository {
    fun create(
        userId: Uuid,
        create: CreateAccessToken,
    ): AccessToken

    fun list(userId: Uuid): List<AccessToken>

    fun get(
        userId: Uuid,
        tokenId: Uuid,
    ): AccessToken

    fun revoke(
        userId: Uuid,
        tokenId: Uuid,
    )

    fun getTokenPermissions(tokenId: Uuid): AccessTokenPermissions?
}

private val LAST_USED_RESOLUTION = 1.minutes

class AccessTokenRepositoryImpl : AccessTokenRepository {
    override fun create(
        userId: Uuid,
        create: CreateAccessToken,
    ): AccessToken {
        if (create.name.isBlank()) throw ErrorResponse.userError("Access tokens must have a name")
        val now = Clock.System.now()
        if (create.expiresAt != null && create.expiresAt <= now) {
            throw ErrorResponse.userError("The access token would expire right away, pick an expiry in the future")
        }
        return transaction {
            val id =
                AccessTokenTable.insertAndGetId {
                    it[user] = userId
                    it[name] = create.name.trim()
                    it[permissions] = create.permissions
                    it[createdAt] = now
                    it[expiresAt] = create.expiresAt
                }
            AccessTokenTable
                .selectAll()
                .where { AccessTokenTable.id eq id }
                .single()
                .toAccessToken()
        }
    }

    override fun list(userId: Uuid): List<AccessToken> =
        transaction {
            AccessTokenTable
                .selectAll()
                .where { AccessTokenTable.user eq userId }
                .orderBy(AccessTokenTable.createdAt to SortOrder.ASC)
                .map { it.toAccessToken() }
        }

    override fun get(
        userId: Uuid,
        tokenId: Uuid,
    ): AccessToken =
        transaction {
            AccessTokenTable
                .selectAll()
                .where { (AccessTokenTable.id eq tokenId) and (AccessTokenTable.user eq userId) }
                .singleOrNull()
                ?.toAccessToken() ?: throw ErrorResponse.notFound("Access token", tokenId)
        }

    override fun revoke(
        userId: Uuid,
        tokenId: Uuid,
    ) {
        val deleted =
            transaction {
                AccessTokenTable.deleteWhere { (AccessTokenTable.id eq tokenId) and (user eq userId) }
            }
        if (deleted == 0) throw ErrorResponse.notFound("Access token", tokenId)
    }

    override fun getTokenPermissions(tokenId: Uuid): AccessTokenPermissions? =
        transaction {
            val row =
                AccessTokenTable
                    .select(AccessTokenTable.lastUsedAt, AccessTokenTable.permissions)
                    .where { AccessTokenTable.id eq tokenId }
                    .singleOrNull() ?: return@transaction null
            val lastUsedAt = row[AccessTokenTable.lastUsedAt]
            val now = Clock.System.now()
            if (lastUsedAt == null || now - lastUsedAt >= LAST_USED_RESOLUTION) {
                AccessTokenTable.update({ AccessTokenTable.id eq tokenId }) { it[this.lastUsedAt] = now }
            }
            row[AccessTokenTable.permissions]
        }
}

private fun ResultRow.toAccessToken() =
    AccessToken(
        id = this[AccessTokenTable.id].value,
        name = this[AccessTokenTable.name],
        createdAt = this[AccessTokenTable.createdAt],
        lastUsedAt = this[AccessTokenTable.lastUsedAt],
        expiresAt = this[AccessTokenTable.expiresAt],
        permissions = this[AccessTokenTable.permissions],
    )
