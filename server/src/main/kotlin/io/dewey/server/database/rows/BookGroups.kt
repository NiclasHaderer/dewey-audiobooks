package io.dewey.server.database.rows

import io.dewey.server.common.extensions.jsonEach
import io.dewey.server.database.tables.BookTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

data class BookGroup(
    val name: String,
    val bookCount: Int,
)

data class BooksInGroup(
    val name: String,
    val books: List<BookRow>,
)

context(_: Transaction)
fun Column<List<String>?>.bookGroups(
    libraryId: Uuid,
    order: SortOrder,
    limit: Int,
    offset: Long,
): List<BookGroup> {
    val eachName = jsonEach()
    val groupName = eachName.value.min()
    val bookCount = BookTable.id.countDistinct()
    return BookTable
        .crossJoin(eachName)
        .select(groupName, bookCount)
        .where { (BookTable.library eq libraryId) and BookTable.visible }
        .groupBy(eachName.value.lowerCase())
        .orderBy(groupName.lowerCase() to order)
        .offset(offset)
        .limit(limit)
        .map { BookGroup(name = it[groupName]!!, bookCount = it[bookCount].toInt()) }
}

context(_: Transaction)
fun Column<List<String>?>.bookGroupCount(libraryId: Uuid): Long {
    val eachName = jsonEach()
    val groups = Count(eachName.value.lowerCase(), distinct = true)
    return BookTable
        .crossJoin(eachName)
        .select(groups)
        .where { (BookTable.library eq libraryId) and BookTable.visible }
        .first()[groups]
}

context(_: Transaction)
fun Column<List<String>?>.booksInGroup(
    name: String,
    libraryId: Uuid,
): BooksInGroup? {
    val eachName = jsonEach()
    val nameMatches =
        exists(
            eachName
                .select(eachName.value)
                .where { eachName.value.lowerCase() eq stringParam(name).lowerCase() },
        )
    val books =
        BookTable
            .selectAll()
            .where { (BookTable.library eq libraryId) and nameMatches and BookTable.visible }
            .orderBy(BookTable.title to SortOrder.ASC)
            .toList()
    if (books.isEmpty()) return null
    val groupName = books.flatMap { it[this].orEmpty() }.filter { it.equals(name, ignoreCase = true) }.min()
    return BooksInGroup(name = groupName, books = books.map { it.toBookRow() })
}
