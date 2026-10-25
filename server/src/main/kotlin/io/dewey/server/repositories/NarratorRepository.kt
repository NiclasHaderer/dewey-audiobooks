package io.dewey.server.repositories

import io.dewey.models.Narrator
import io.dewey.models.NarratorDetailed
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.rows.bookGroupCount
import io.dewey.server.database.rows.bookGroups
import io.dewey.server.database.rows.booksInGroup
import io.dewey.server.database.rows.booksToModels
import io.dewey.server.database.tables.BookTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

interface NarratorRepository {
    fun getAll(
        libraryId: Uuid,
        order: SortOrder,
        limit: Int = 20,
        offset: Long = 0L,
    ): List<Narrator>

    fun get(
        userId: Uuid,
        name: String,
        libraryId: Uuid,
    ): NarratorDetailed

    fun total(libraryId: Uuid): Long
}

class NarratorRepositoryImpl : NarratorRepository {
    override fun getAll(
        libraryId: Uuid,
        order: SortOrder,
        limit: Int,
        offset: Long,
    ): List<Narrator> =
        transaction {
            BookTable.narrators
                .bookGroups(libraryId, order, limit, offset)
                .map { Narrator(name = it.name, bookCount = it.bookCount) }
        }

    override fun get(
        userId: Uuid,
        name: String,
        libraryId: Uuid,
    ): NarratorDetailed =
        transaction {
            val match =
                BookTable.narrators.booksInGroup(name, libraryId)
                    ?: throw ErrorResponse.notFound("Narrator", name)
            NarratorDetailed(name = match.name, books = booksToModels(match.books, userId))
        }

    override fun total(libraryId: Uuid): Long = transaction { BookTable.narrators.bookGroupCount(libraryId) }
}
