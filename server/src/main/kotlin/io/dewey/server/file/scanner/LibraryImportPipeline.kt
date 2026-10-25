package io.dewey.server.file.scanner

import io.dewey.server.common.ConcurrentUniqQueue
import io.dewey.server.common.extensions.hasAudioExtension
import io.dewey.server.config.DeweyConfig
import io.dewey.server.database.tables.LibraryTable
import io.dewey.server.file.TrackManager
import io.dewey.server.file.analyzer.AudioFileAnalysisResult
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.withLock
import kotlin.io.path.name
import kotlin.io.path.readAttributes
import kotlin.uuid.Uuid

private sealed interface WriteCommand {
    data class UpsertTrack(
        val result: AudioFileAnalysisResult,
        val libraryId: Uuid,
    ) : WriteCommand

    data class TouchTracks(
        val paths: List<Path>,
        val libraryId: Uuid,
    ) : WriteCommand

    data class TouchFolder(
        val path: Path,
        val libraryId: Uuid,
    ) : WriteCommand

    data class RemoveFile(
        val path: Path,
        val libraryId: Uuid,
    ) : WriteCommand

    data class RemoveSubtree(
        val path: Path,
        val libraryId: Uuid,
    ) : WriteCommand

    data class ReapStaleTracks(
        val libraryId: Uuid,
    ) : WriteCommand

    data class PruneOrphans(
        val libraryId: Uuid,
    ) : WriteCommand
}

data class ScanRequest(
    val libraryId: Uuid,
    val reanalyze: Boolean = false,
)

@OptIn(ExperimentalAtomicApi::class)
class LibraryImportPipeline : KoinComponent {
    private val log = logger {}
    private val config by inject<DeweyConfig>()
    private val trackManager by inject<TrackManager>()
    private val roots by inject<LibraryRoots>()
    private val cleanup by inject<LibraryCleanup>()

    // A queued folder stands for its whole subtree, so it subsumes every descendant already waiting
    private val watchQueue =
        ConcurrentUniqQueue<Path> { wider, narrower -> narrower != wider && narrower.startsWith(wider) }
    private val scanQueue = ConcurrentUniqQueue<Path>()
    private val dbWriterQueue = LinkedBlockingQueue<WriteCommand>(128)

    private val onlyOneScanAtATimeLock = ReentrantLock()

    @Volatile
    private var scanState: ScanState? = null

    private var running = AtomicBoolean(false)
    private var watchWorkers: List<Thread> = emptyList()
    private var dbWriter: Thread? = null

    @Volatile
    private var drainWriter = false

    private class ScanState(
        val library: LibraryEntityModel,
        val reanalyze: Boolean,
    ) {
        @Volatile
        var noNewFilesWillBeAdded = false
    }

    // Not thread safe
    fun start() {
        if (!running.compareAndSet(expectedValue = false, newValue = true)) return
        dbWriter = startWriter()
        watchWorkers = startWorkers("watch", ::watchWorkerLoop)
        log.info { "Import pipeline started with ${watchWorkers.size} worker(s)" }
    }

    // Not thread safe
    fun stop() {
        if (!running.compareAndSet(expectedValue = true, newValue = false)) return
        watchWorkers.forEach {
            it.interrupt()
            it.join()
        }
        dbWriter?.interrupt()
        dbWriter?.join()
        watchWorkers = emptyList()
        dbWriter = null
    }

    val scanning: Boolean
        get() = scanState != null

    fun enqueue(path: Path) = watchQueue.add(path)

    fun scanLibrary(
        libraryId: Uuid,
        reanalyze: Boolean = false,
    ): Boolean {
        val library = roots.of(libraryId)
        if (library == null) {
            log.warn { "Library $libraryId no longer exists, not scanning it" }
            return false
        }
        log.info { "Scanning library '${library.name}'" }
        // The bump and the walk are one operation: everything the walk does not stamp is what the sweep collects
        transaction {
            val current =
                LibraryTable
                    .select(LibraryTable.scanIndex)
                    .where { LibraryTable.id eq libraryId }
                    .single()[LibraryTable.scanIndex]
            LibraryTable.update({ LibraryTable.id eq libraryId }) { it[scanIndex] = current + 1uL }
        }
        walkLibrary(library, reanalyze)
        return true
    }

    internal fun walkLibrary(
        library: LibraryEntityModel,
        reanalyze: Boolean = false,
    ) = onlyOneScanAtATimeLock.withLock {
        val state = ScanState(library, reanalyze)
        scanState = state
        // A joined thread is done, so this is the whole proof that no watch event is still being processed
        watchWorkers.forEach { it.join() }
        watchWorkers = emptyList()
        awaitWritesApplied()

        val scanWorkers = startWorkers("scan") { scanWorkerLoop(state) }
        walk(library, state)
        state.noNewFilesWillBeAdded = true
        scanWorkers.forEach { it.join() }

        // Unconditional: whatever could not be read was stamped as seen, so nothing left unstamped is
        // anything but gone
        dbWriterQueue.put(WriteCommand.ReapStaleTracks(library.id))
        dbWriterQueue.put(WriteCommand.PruneOrphans(library.id))
        awaitWritesApplied()

        scanState = null
        this@LibraryImportPipeline.watchWorkers = startWorkers("watch", ::watchWorkerLoop)
    }

    private fun walk(
        library: LibraryEntityModel,
        state: ScanState,
    ) {
        for (root in library.folders) {
            // an unreadable or empty root is far more likely to be a broken mount
            // than a library someone emptied, and the sweep cannot tell the difference.
            val children = runCatching { Files.newDirectoryStream(root).use { it.count() } }.getOrDefault(0)
            if (children == 0) {
                log.error { "Library root '$root' is missing, unreadable or empty" }
                keepSubtree(root, library.id)
                continue
            }
            walkIgnoreAware(
                root,
                onIgnoredDirectory = { log.debug { "Ignoring directory '$it'" } },
                onFailure = { path, failure ->
                    // Its files were never listed, so there is nothing to stamp by name. Stamping by prefix
                    // is what keeps a folder we could not read from reading as a folder that was emptied.
                    log.error(failure) { "Could not read '$path'" }
                    keepSubtree(path, library.id)
                },
            ) { file, attrs ->
                if (!attrs.isSymbolicLink && file.hasAudioExtension()) scanQueue.add(file)
            }
        }
    }

    private fun keepSubtree(
        path: Path,
        libraryId: Uuid,
    ) = dbWriterQueue.put(WriteCommand.TouchFolder(path, libraryId))

    private fun startWorkers(
        name: String,
        loop: () -> Unit,
    ): List<Thread> =
        List(config.importThreads) { index ->
            Thread(loop, "import-$name-$index").also {
                it.isDaemon = true
                it.start()
            }
        }

    private fun watchWorkerLoop() {
        while (running.load() && scanState == null) {
            val path =
                try {
                    watchQueue.poll()
                } catch (_: InterruptedException) {
                    continue
                }
            if (path == null) continue
            try {
                processWatch(path)
            } catch (throwable: Throwable) {
                log.error(throwable) { "Could not process '$path'" }
            }
        }
    }

    private fun scanWorkerLoop(state: ScanState) {
        while (running.load()) {
            val finished = state.noNewFilesWillBeAdded
            val path = try {
                scanQueue.poll()
            } catch (_: InterruptedException) {
                continue
            }
            if (path == null) {
                if (finished) return
                continue
            }
            try {
                importFile(path, state.library, state.reanalyze)
            } catch (throwable: Throwable) {
                // Listed but unreadable. Keeping the stamp is the whole difference between "I could not read
                // this" and "this is gone", and only the second one may delete a track.
                log.error(throwable) { "Could not process '$path'" }
                dbWriterQueue.put(WriteCommand.TouchTracks(listOf(path), state.library.id))
            }
        }
    }

    private fun processWatch(path: Path) {
        val library = roots.owning(path) ?: return
        val root = library.folders.first { path.startsWith(it) }

        val attributes =
            try {
                path.readAttributes<BasicFileAttributes>(LinkOption.NOFOLLOW_LINKS)
            } catch (_: NoSuchFileException) {
                null
            } catch (unreadable: IOException) {
                // Not gone, only unreadable: a chmod or a mount that blinked must never turn into a delete.
                // If it really is gone, the next stat says so and the scan sweep collects it.
                log.warn(unreadable) { "Skipping '$path': could not tell whether it exists" }
                return
            }

        if (path.name == IGNORE_FILE) {
            val folder = path.parent ?: return
            // Inverted: the marker appearing means the folder has to go, and it disappearing brings it back
            if (attributes != null) {
                removeSubtree(folder, library.id)
            } else {
                watchQueue.add(folder)
            }
            return
        }

        if (attributes == null) {
            removeSubtree(path, library.id)
            return
        }

        // The only upward marker walk in the system: watch events arrive as a bare path with no context
        if (isIgnored(path, root)) {
            removeSubtree(path, library.id)
            return
        }

        if (attributes.isDirectory) {
            walkIgnoreAware(
                path,
                onIgnoredDirectory = { removeSubtree(it, library.id) },
            ) { file, attrs ->
                if (!attrs.isSymbolicLink && file.hasAudioExtension()) watchQueue.add(file)
            }
            return
        }

        if (!path.hasAudioExtension()) return

        // A file that is still being copied parses as a valid but wrong track, so wait until it stops changing.
        // A future mtime is a file server whose clock runs ahead, which must not mean "never import this".
        val age = System.currentTimeMillis() - attributes.lastModifiedTime().toMillis()
        if (age in 0..<config.settleMillis) {
            watchQueue.add(path)
            // Without this the path is popped again immediately, and the worker spins on stat for the whole copy
            // TODO not great, but leave it for now...
            Thread.sleep(config.settleMillis)
            return
        }

        importFile(path, library)
    }

    private fun importFile(
        path: Path,
        library: LibraryEntityModel,
        reanalyze: Boolean = false,
    ) {
        if (!reanalyze && !trackManager.needsAnalysis(path)) {
            dbWriterQueue.put(WriteCommand.TouchTracks(listOf(path), library.id))
            return
        }
        val outcome = trackManager.analyze(path, library)
        if (outcome != null) {
            dbWriterQueue.put(WriteCommand.UpsertTrack(outcome, library.id))
        } else {
            // The walk listed it, so it exists; analysis failing says nothing about that
            dbWriterQueue.put(WriteCommand.TouchTracks(listOf(path), library.id))
        }
    }

    private fun removeSubtree(
        folder: Path,
        libraryId: Uuid,
    ) {
        watchQueue.removeAll { it.startsWith(folder) }
        dbWriterQueue.put(WriteCommand.RemoveSubtree(folder, libraryId))
        dbWriterQueue.put(WriteCommand.PruneOrphans(libraryId))
    }

    private fun writerLoop() {
        while (running.load()) {
            // Read before the take, for the same reason the scan workers do it: an empty queue only means
            // there is nothing left if nobody is adding any more
            val stopping = drainWriter
            val command =
                try {
                    dbWriterQueue.poll(10, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    continue
                }
            if (command == null) {
                if (stopping) return
                continue
            }
            try {
                apply(command)
            } catch (throwable: Throwable) {
                log.error(throwable) { "Could not apply $command" }
            }
        }
    }

    // Every producer is joined before this runs, so a joined writer means every queued command was applied
    private fun awaitWritesApplied() {
        drainWriter = true
        dbWriter?.join()
        drainWriter = false
        dbWriter = startWriter()
    }

    private fun startWriter(): Thread =
        Thread(::writerLoop, "import-writer").also {
            it.isDaemon = true
            it.start()
        }

    private fun apply(command: WriteCommand) {
        when (command) {
            is WriteCommand.UpsertTrack -> {
                trackManager.insert(command.result, command.libraryId)
            }

            is WriteCommand.TouchTracks -> {
                trackManager.touch(command.paths, command.libraryId)
            }

            is WriteCommand.TouchFolder -> {
                trackManager.touchFolder(command.path, command.libraryId)
            }

            is WriteCommand.RemoveFile -> {
                trackManager.removeFile(command.path, command.libraryId)
            }

            is WriteCommand.RemoveSubtree -> {
                trackManager.removeFolder(command.path, command.libraryId)
            }

            is WriteCommand.ReapStaleTracks -> {
                cleanup.removeStaleTracks(command.libraryId)
            }

            is WriteCommand.PruneOrphans -> {
                cleanup.removeOrphans(command.libraryId)
            }
        }
    }
}
