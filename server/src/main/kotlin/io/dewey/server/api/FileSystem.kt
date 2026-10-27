package io.dewey.server.api

import io.dewey.openapi.ktor.errors.ErrorResponse
import io.dewey.openapi.ktor.get
import io.ktor.server.routing.Routing
import java.io.File

fun Routing.fileSystemRouting() {
    get<Api.FileSystem, List<FileSystemItem>> { (path, showHidden) ->
        val directory = File(path)

        if (!directory.exists() || !directory.isDirectory) throw ErrorResponse.notFound("Directory", path)

        val children =
            directory.listFiles()
                ?: throw ErrorResponse.forbidden("The server cannot read directory $path")

        children
            .filter { it.isDirectory && (showHidden || !it.isHidden) && !it.listFiles().isNullOrEmpty() }
            .map { FileSystemItem(name = it.name, path = it.path, parent = it.parentFile?.path) }
            .sortedBy { it.name }
    }
}
