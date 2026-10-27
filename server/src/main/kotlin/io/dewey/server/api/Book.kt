package io.dewey.server.api

import io.dewey.models.Book
import io.dewey.models.BookDetailed
import io.dewey.models.BookUpdate
import io.dewey.models.PaginatedResponse
import io.dewey.models.TitledId
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.patch
import io.dewey.openapi.ktor.post
import io.dewey.server.plugins.auth.deweyPrincipal
import io.dewey.server.repositories.BookRepository
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject

fun Routing.bookRouting() {
    val bookRepository by inject<BookRepository>()
    get<Api.Libraries.Id.Books.All, PaginatedResponse<Book>> { route ->
        val books =
            bookRepository.getAll(
                userId = deweyPrincipal().userId,
                libraryId = route.libraryId,
                order = route.order.toSortOrder(),
                limit = route.limit,
                offset = route.offset,
                showInvisible = route.showInvisible,
            )
        PaginatedResponse(
            items = books,
            total = bookRepository.total(libraryId = route.libraryId, showInvisible = route.showInvisible),
            limit = route.limit,
            offset = route.offset,
        )
    }

    get<Api.Libraries.Id.Books.Id, BookDetailed> { route ->
        bookRepository.get(userId = deweyPrincipal().userId, id = route.id, libraryId = route.libraryId)
    }

    get<Api.Libraries.Id.Books.Autocomplete, List<TitledId>> { route ->
        bookRepository.search(deweyPrincipal().userId, route.query, route.libraryId).map { TitledId(it.id, it.title) }
    }

    patch<Api.Libraries.Id.Books.Id, BookUpdate, Book> { route, patch ->
        bookRepository.modify(deweyPrincipal().userId, route.id, route.libraryId, patch)
    }

    post<Api.Libraries.Id.Books.Id.AutoMatch, Unit, Book> { id, _ ->
        bookRepository.autoMatch(deweyPrincipal().userId, id.id, id.libraryId)
    }
}
