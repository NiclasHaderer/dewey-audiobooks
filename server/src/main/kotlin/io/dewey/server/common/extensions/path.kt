package io.dewey.server.common.extensions

import io.dewey.server.common.AUDIO_TYPES
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name
import kotlin.time.Instant

private fun Path.hasParent() = this.parent != null && this.parent.name.isNotEmpty()

// Both sides of a "is this path inside that root" test have to be canonicalised the same way, or a symlinked
// ancestor (/var on macOS) makes them compare unequal. Unlike toRealPath this also works for a path that does
// not exist, which matters because the watcher reports deletions only after the fact.
fun Path.canonical(): Path = runCatching { toFile().canonicalFile.toPath() }.getOrDefault(absolute().normalize())

// The one spelling of a file path that is written to and looked up in the database
fun Path.canonicalString(): String = canonical().absolutePathString()

/** This path relative to [base], or null if it is not inside [base]. */
fun Path.relativeToBase(base: Path): Path? {
    val absBase = base.canonical()
    val absPath = canonical()
    return if (absPath.startsWith(absBase)) absBase.relativize(absPath) else null
}

fun Path.countParents(): Int {
    var current = this.normalize()
    var parents = 0
    while (current.hasParent()) {
        current = current.parent
        parents += 1
    }
    return parents
}

fun Path.parentName() = this.parent.name

fun Path.grandParentName() = this.parent.parent.name

fun Path.grandGrandParentName() = this.parent.parent.parent.name

fun Path.hasAudioExtension(): Boolean = this.extension.lowercase() in AUDIO_TYPES

fun Path.lastModifiedInstant(): Instant = Instant.fromEpochMilliseconds(getLastModifiedTime().toMillis())
