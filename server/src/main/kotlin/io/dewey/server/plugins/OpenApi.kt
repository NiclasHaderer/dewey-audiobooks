package io.dewey.server.plugins

import io.dewey.openapi.ktor.plugins.OpenAPIRouting
import io.dewey.openapi.ktor.plugins.OpenAPIWebUI
import io.dewey.server.config.DeweyConfig
import io.dewey.server.plugins.auth.Guards
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.dataconversion.DataConversion
import org.koin.ktor.ext.inject

fun Application.configureOpenApi() {
    val deweyConfig by inject<DeweyConfig>()

    install(DataConversion)
    install(OpenAPIRouting) {
        info {
            title = "Dewey"
            version = "0.0.2"
            description = "Audiobook server"
        }
        addServer { url = deweyConfig.baseUrl }
        components {
            securitySchemes {
                for (guard in Guards.All) {
                    http(guard) {
                        scheme = "bearer"
                        bearerFormat = "JWT"
                    }
                }
            }
        }
    }
    install(OpenAPIWebUI)
}
