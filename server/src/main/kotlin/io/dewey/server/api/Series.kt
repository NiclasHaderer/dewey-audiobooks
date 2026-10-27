package io.dewey.server.api

import io.dewey.models.PaginatedResponse
import io.dewey.models.Series
import io.dewey.models.SeriesCreate
import io.dewey.models.SeriesDetailed
import io.dewey.models.SeriesUpdate
import io.dewey.models.TitledId
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.patch
import io.dewey.openapi.ktor.post
import io.dewey.server.plugins.auth.deweyPrincipal
import io.dewey.server.repositories.SeriesRepository
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject

fun Routing.seriesRouting() {
    val seriesRepository by inject<SeriesRepository>()
    get<Api.Libraries.Id.Series.All, PaginatedResponse<Series>> {
        PaginatedResponse(
            seriesRepository.getAll(
                userId = deweyPrincipal().userId,
                libraryId = it.libraryId,
                order = it.order.toSortOrder(),
                limit = it.limit,
                offset = it.offset,
                showInvisible = it.showInvisible,
            ),
            offset = it.offset,
            limit = it.limit,
            total = seriesRepository.total(libraryId = it.libraryId, showInvisible = it.showInvisible),
        )
    }

    get<Api.Libraries.Id.Series.Id, SeriesDetailed> {
        seriesRepository.get(userId = deweyPrincipal().userId, id = it.id, libraryId = it.libraryId)
    }

    get<Api.Libraries.Id.Series.Autocomplete, List<TitledId>> {
        seriesRepository
            .search(userId = deweyPrincipal().userId, query = it.query, libraryId = it.libraryId)
            .map { series -> TitledId(id = series.id, title = series.title) }
    }

    patch<Api.Libraries.Id.Series.Id, SeriesUpdate, Series> { id, patchSeries ->
        seriesRepository.modify(
            userId = deweyPrincipal().userId,
            id = id.id,
            libraryId = id.libraryId,
            partial = patchSeries,
        )
    }

    post<Api.Libraries.Id.Series, SeriesCreate, SeriesDetailed> { route, postSeries ->
        val series = seriesRepository.createManual(postSeries.title, route.libraryId)
        seriesRepository.get(deweyPrincipal().userId, series.id, route.libraryId, showInvisible = true)
    }

    post<Api.Libraries.Id.Series.Id.AutoMatch, Unit, Series> { id, _ ->
        seriesRepository.autoMatch(deweyPrincipal().userId, id.id, id.libraryId)
    }
}
