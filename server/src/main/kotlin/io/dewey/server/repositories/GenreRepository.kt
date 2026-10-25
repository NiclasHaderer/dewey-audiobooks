package io.dewey.server.repositories

import io.dewey.models.Genre
import io.dewey.models.GenreDetailed
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.server.database.rows.bookGroupCount
import io.dewey.server.database.rows.bookGroups
import io.dewey.server.database.rows.booksInGroup
import io.dewey.server.database.rows.booksToModels
import io.dewey.server.database.tables.BookTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

interface GenreRepository {
    fun getAll(
        libraryId: Uuid,
        order: SortOrder,
        limit: Int = 20,
        offset: Long = 0L,
    ): List<Genre>

    fun get(
        userId: Uuid,
        name: String,
        libraryId: Uuid,
    ): GenreDetailed

    fun total(libraryId: Uuid): Long
}

class GenreRepositoryImpl : GenreRepository {
    override fun getAll(
        libraryId: Uuid,
        order: SortOrder,
        limit: Int,
        offset: Long,
    ): List<Genre> =
        transaction {
            BookTable.genres
                .bookGroups(libraryId, order, limit, offset)
                .map { Genre(name = it.name, bookCount = it.bookCount) }
        }

    override fun get(
        userId: Uuid,
        name: String,
        libraryId: Uuid,
    ): GenreDetailed =
        transaction {
            val match =
                BookTable.genres.booksInGroup(name, libraryId)
                    ?: throw ErrorResponse.notFound("Genre", name)
            GenreDetailed(name = match.name, books = booksToModels(match.books, userId))
        }

    override fun total(libraryId: Uuid): Long = transaction { BookTable.genres.bookGroupCount(libraryId) }
}
