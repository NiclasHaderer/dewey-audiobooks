package io.dewey.server

import io.dewey.openapi.client.kotlin.KtErrorHandling
import io.dewey.openapi.client.kotlin.generateKotlinClient
import io.dewey.openapi.client.typescript.generateTsClient
import io.dewey.openapi.ktor.plugins.generateOpenApiSpec
import io.dewey.server.config.DeweyConfig
import io.dewey.server.di.setupDependencyInjection
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.core.context.stopKoin
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

fun main() {
    val dataDir = createTempDirectory("dewey-client-gen")
    setupDependencyInjection(DeweyConfig(dataDir = dataDir))
    try {
        embeddedServer(Netty, port = 0) {
            plugins()
            routing()
            generateTsClient("web/src/client/generated")
            generateKotlinClient(
                apiClientPackageName = "io.dewey.client",
                savePath = "client/src/main/kotlin/io/dewey/client",
                apiClientName = "DeweyClient",
                errorHandling = KtErrorHandling.Exception,
            )
            generateOpenApiSpec(Path.of("gen/openapi.yaml"))
        }.apply {
            start(wait = false)
            stop()
        }
    } finally {
        stopKoin()
        dataDir.toFile().deleteRecursively()
    }
}
