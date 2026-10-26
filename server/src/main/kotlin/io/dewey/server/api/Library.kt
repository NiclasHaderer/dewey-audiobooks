package io.dewey.server.api

import io.dewey.models.Library
import io.dewey.models.LibrarySearchResult
import io.dewey.openapi.ktor.delete
import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.patch
import io.dewey.openapi.ktor.post
import io.dewey.server.common.scheduling.Scheduler
import io.dewey.server.plugins.auth.deweyPrincipal
import io.dewey.server.repositories.LibraryRepository
import io.dewey.server.repositories.SearchRepository
import io.dewey.server.schedules.DeweySchedules
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import org.koin.ktor.ext.inject

fun Routing.libraryRouting() {
    val scheduler by inject<Scheduler>()
    val schedules by inject<DeweySchedules>()

    val libraryRepository by inject<LibraryRepository>()

    get<Api.Libraries, List<Library>> {
        val allowed = deweyPrincipal().permissions.libraries.mapTo(mutableSetOf()) { it.id }
        libraryRepository.getAll().filter { it.id in allowed }
    }

    get<Api.Libraries.Id, Library> { (id) -> libraryRepository.get(id) }

    patch<Api.Libraries.Id, PartialUpdateLibrary, Library> { (id), patchLibrary ->
        requireAdmin("update")
        libraryRepository.modify(id, patchLibrary)
    }

    post<Api.Libraries.Id.Rescan, Unit, Unit> { route, _ -> libraryRepository.rescan(route.libraryId) }

    post<Api.Libraries, UpdateLibrary, Library> { _, postLibrary ->
        requireAdmin("create")
        libraryRepository.create(postLibrary)
    }

    delete<Api.Libraries.Id, Unit, Unit> { (id), _ ->
        requireAdmin("delete")
        libraryRepository.delete(id)
    }

    get<Api.Libraries.Search, LibrarySearchResult> {
        val libsToSearch = deweyPrincipal().permissions.libraries.map { lib -> lib.id }

        val query =
            it.q ?: throw ErrorResponse.notImplemented(
                "This is still under construction. Currently only the parameter 'q' is supported",
            )

        SearchRepository.everywhere(query, libsToSearch, deweyPrincipal().userId)
    }
}

// Folders point the server at any path it can read, so a library is only ever set up by an admin
private fun RoutingContext.requireAdmin(change: String) {
    if (!deweyPrincipal().permissions.isAdmin) throw ErrorResponse.forbidden("Only admins can $change libraries")
}
