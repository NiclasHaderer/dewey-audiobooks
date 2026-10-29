package io.dewey.server.api

import io.dewey.models.Author
import io.dewey.models.AuthorCreate
import io.dewey.models.AuthorDetailed
import io.dewey.models.AuthorUpdate
import io.dewey.models.NamedId
import io.dewey.models.PaginatedResponse
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.patch
import io.dewey.openapi.ktor.post
import io.dewey.server.plugins.auth.deweyPrincipal
import io.dewey.server.repositories.AuthorRepository
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject

fun Routing.authorRouting() {
    val authorService by inject<AuthorRepository>()

    get<Api.Libraries.Id.Authors.All, PaginatedResponse<Author>> {
        PaginatedResponse(
            items =
                authorService.getAll(
                    deweyPrincipal().userId,
                    it.libraryId,
                    it.order.toSortOrder(),
                    it.limit,
                    it.offset,
                    it.showInvisible,
                ),
            limit = it.limit,
            offset = it.offset,
            total = authorService.total(it.libraryId, it.showInvisible),
        )
    }
    get<Api.Libraries.Id.Authors.Id, AuthorDetailed> { authorService.get(deweyPrincipal().userId, it.id, it.libraryId) }

    get<Api.Libraries.Id.Authors.Autocomplete, List<NamedId>> {
        authorService.search(deweyPrincipal().userId, it.query, it.libraryId).map { NamedId(it.id, it.name) }
    }

    patch<Api.Libraries.Id.Authors.Id, AuthorUpdate, Author> { id, patchAuthor ->
        authorService.modify(deweyPrincipal().userId, id.id, id.libraryId, patchAuthor)
    }

    post<Api.Libraries.Id.Authors, AuthorCreate, AuthorDetailed> { route, postAuthor ->
        val author = authorService.createManual(postAuthor.name, route.libraryId)
        authorService.get(deweyPrincipal().userId, author.id, route.libraryId, showInvisible = true)
    }

    post<Api.Libraries.Id.Authors.Id.AutoMatch, Unit, Author> { id, _ ->
        authorService.autoMatch(deweyPrincipal().userId, id.id, id.libraryId)
    }
}
