package io.dewey.server

import io.dewey.metadata.MetadataProviderUnavailableException
import io.dewey.openapi.ktor.errors.configureStatusPages
import io.dewey.server.api.audioRouting
import io.dewey.server.api.authRoutes
import io.dewey.server.api.authorRouting
import io.dewey.server.api.bookRouting
import io.dewey.server.api.eventRouting
import io.dewey.server.api.fileSystemRouting
import io.dewey.server.api.genreRouting
import io.dewey.server.api.imageRouting
import io.dewey.server.api.libraryRouting
import io.dewey.server.api.licenseRouting
import io.dewey.server.api.metadataAgentRouting
import io.dewey.server.api.metadataRouting
import io.dewey.server.api.narratorRouting
import io.dewey.server.api.pingRouting
import io.dewey.server.api.progressRouting
import io.dewey.server.api.scannerRouting
import io.dewey.server.api.seriesRouting
import io.dewey.server.api.versionRouting
import io.dewey.server.common.scheduling.Scheduler
import io.dewey.server.config.DeweyConfig
import io.dewey.server.database.DatabaseConnector
import io.dewey.server.di.setupDependencyInjection
import io.dewey.server.file.scanner.LibraryImportPipeline
import io.dewey.server.file.scanner.LibraryWatcher
import io.dewey.server.plugins.auth.configureAuthentication
import io.dewey.server.plugins.configureMonitoring
import io.dewey.server.plugins.configureOpenApi
import io.dewey.server.plugins.configurePartialContent
import io.dewey.server.plugins.configureRouting
import io.dewey.server.plugins.configureSecurityHeaders
import io.dewey.server.plugins.configureSerialization
import io.dewey.server.plugins.configureSockets
import io.dewey.server.schedules.AutoMatcher
import io.dewey.server.schedules.DeweySchedules
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import org.slf4j.bridge.SLF4JBridgeHandler
import java.util.logging.LogManager

fun main() {
    // Force every library using the standard java logger to use SLF4J
    LogManager.getLogManager().reset()
    SLF4JBridgeHandler.install()

    // Loaded before the server starts so a broken config fails immediately instead of on first injection
    val config = DeweyConfig.load()

    // Start the server
    embeddedServer(
        Netty,
        port = config.port,
        watchPaths = emptyList(),
        host = "0.0.0.0",
        module = { applicationModule(config) },
    ).start(wait = true)
}

fun Application.applicationModule(config: DeweyConfig) {
    setupDependencyInjection(config)
    DatabaseConnector.connect()
    plugins()
    routing()
    startBackgroundJobs()
}

fun Application.plugins() {
    configureStatusPages {
        status<MetadataProviderUnavailableException>(HttpStatusCode.BadGateway)
    }
    configureRouting()
    configureSecurityHeaders()
    configureSerialization()
    configureOpenApi()
    configurePartialContent()
    configureSockets()
    configureMonitoring()
    configureAuthentication()
}

fun Application.routing() {
    routing {
        // Authentication
        authRoutes()

        // List directories
        fileSystemRouting()

        // Libraries and their resources
        libraryRouting()
        scannerRouting()
        metadataAgentRouting()

        // Library resources
        bookRouting()
        seriesRouting()
        authorRouting()
        narratorRouting()
        genreRouting()

        // Metadata for the resources
        metadataRouting()

        // Per-user listening progress and history
        progressRouting()

        // Change events over a websocket
        eventRouting()

        // Static files
        audioRouting()
        imageRouting()

        // Routes for checking if the server is available
        pingRouting()
        versionRouting()

        // Attribution for the dependencies shipped in the jar
        licenseRouting()
    }
}

fun Application.startBackgroundJobs() {
    val scheduler = get<Scheduler>()
    val deweySchedules = get<DeweySchedules>()
    scheduler.schedule(deweySchedules.fullScan)
    scheduler.launchNow(deweySchedules.fullScan)
    launch { scheduler.start() }
    val autoMatcher = get<AutoMatcher>().also { it.start() }
    val pipeline = get<LibraryImportPipeline>().also {
        it.start()
    }
    val watcher = get<LibraryWatcher>().also {
        launch { it.start() }
    }
    monitor.subscribe(ApplicationStopping) {
        watcher.stop()
        pipeline.stop()
        autoMatcher.stop()
    }
}
