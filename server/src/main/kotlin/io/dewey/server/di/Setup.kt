package io.dewey.server.di

import io.dewey.metadata.MetadataAgents
import io.dewey.metadata.audible.AudibleMetadataAgent
import io.dewey.metadata.audiobookdb.AudiobookDbMetadataAgent
import io.dewey.metadata.libby.LibbyMetadataAgent
import io.dewey.server.common.ImageDownloader
import io.dewey.server.common.scheduling.Scheduler
import io.dewey.server.config.DeweyConfig
import io.dewey.server.events.ChangeEvents
import io.dewey.server.file.TrackManager
import io.dewey.server.file.analyzer.AudioFileAnalyzers
import io.dewey.server.file.analyzer.impl.AudioFolderScanner
import io.dewey.server.file.analyzer.impl.AudioTagScanner
import io.dewey.server.file.scanner.LibraryCleanup
import io.dewey.server.file.scanner.LibraryImportPipeline
import io.dewey.server.file.scanner.LibraryRoots
import io.dewey.server.file.scanner.LibraryWatcher
import io.dewey.server.file.scanner.LibraryWatcherImpl
import io.dewey.server.repositories.AuthorRepository
import io.dewey.server.repositories.AuthorServiceImpl
import io.dewey.server.repositories.BookRepository
import io.dewey.server.repositories.BookRepositoryImpl
import io.dewey.server.repositories.GenreRepository
import io.dewey.server.repositories.GenreRepositoryImpl
import io.dewey.server.repositories.LibraryRepository
import io.dewey.server.repositories.LibraryRepositoryImpl
import io.dewey.server.repositories.NarratorRepository
import io.dewey.server.repositories.NarratorRepositoryImpl
import io.dewey.server.repositories.ProgressRepository
import io.dewey.server.repositories.ProgressRepositoryImpl
import io.dewey.server.repositories.SeriesRepository
import io.dewey.server.repositories.SeriesRepositoryImpl
import io.dewey.server.schedules.AutoMatcher
import io.dewey.server.schedules.DeweySchedules
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.logger.slf4jLogger

fun deweyModule(config: DeweyConfig) =
    module {
        single { config }
        single<MetadataAgents> {
            MetadataAgents(listOf(AudibleMetadataAgent(), LibbyMetadataAgent(), AudiobookDbMetadataAgent()))
        }
        single<AudioFileAnalyzers> { AudioFileAnalyzers(listOf(AudioTagScanner(), AudioFolderScanner())) }
        single { ImageDownloader() }
        single { LibraryRoots() }
        single { LibraryCleanup() }
        single { TrackManager() }
        single<LibraryImportPipeline> { LibraryImportPipeline() }
        single<LibraryWatcher> { LibraryWatcherImpl() }
        single<BookRepository> { BookRepositoryImpl() }
        single<AuthorRepository> { AuthorServiceImpl() }
        single<SeriesRepository> { SeriesRepositoryImpl() }
        single<NarratorRepository> { NarratorRepositoryImpl() }
        single<GenreRepository> { GenreRepositoryImpl() }
        single<LibraryRepository> { LibraryRepositoryImpl() }
        single<ProgressRepository> { ProgressRepositoryImpl() }
        single { Scheduler() }
        single { DeweySchedules() }
        single { AutoMatcher() }
        single { ChangeEvents() }
    }

fun setupDependencyInjection(config: DeweyConfig) {
    startKoin {
        modules(deweyModule(config))
        slf4jLogger()
    }
}
