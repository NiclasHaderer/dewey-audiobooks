package io.dewey.server.database.access

import io.dewey.server.common.imageContentType
import io.dewey.server.database.tables.ImageTable
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.uuid.Uuid

private val log = logger {}

context(_: Transaction)
private fun createImage(imageBytes: ByteArray): Uuid =
    ImageTable.insertAndGetId { it[image] = ExposedBlob(imageBytes) }.value

context(_: Transaction)
fun getOrCreateImage(
    newImage: ByteArray?,
    currentImageID: Uuid?,
): Uuid? {
    if (newImage == null) return currentImageID
    // Cover art embedded in a track can be anything at all. Dropping it keeps a single odd file from failing
    // the whole import, which throwing here would do.
    if (imageContentType(newImage) == null) {
        log.warn { "Ignoring cover art that is not a supported image" }
        return currentImageID
    }
    val currentBytes =
        currentImageID?.let { id ->
            ImageTable
                .select(ImageTable.image)
                .where { ImageTable.id eq id }
                .singleOrNull()
                ?.get(ImageTable.image)
                ?.bytes
        }
    return if (currentBytes?.contentEquals(newImage) == true) currentImageID else createImage(newImage)
}
