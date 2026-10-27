package io.dewey.openapi.ktor.errors

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.cio.ChannelWriteException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@PublishedApi
internal class ErrorBody(
    val error: String,
    val status: Int,
    val details: JsonElement? = null,
)

@PublishedApi
internal fun logCommitted(
    logger: KLogger,
    call: ApplicationCall,
    cause: Throwable,
) {
    logger.error(cause) {
        "Failed after the response to ${call.request.httpMethod.value} ${call.request.path()} was committed, " +
            "so the client received a truncated body"
    }
}

@PublishedApi
internal fun <T : Throwable> formatException(
    logger: KLogger,
    statusCode: HttpStatusCode,
    cb: ((cause: T) -> Unit)? = null,
): suspend (call: ApplicationCall, cause: T) -> Unit =
    { call, cause ->
        if (call.response.isSent) {
            logCommitted(logger, call, cause)
        } else {
            call.respond(
                statusCode,
                ErrorBody(cause.message ?: statusCode.description, statusCode.value),
            )
        }
        cb?.invoke(cause)
    }

class ErrorStatuses
    internal constructor(
        @PublishedApi internal val config: StatusPagesConfig,
        @PublishedApi internal val logger: KLogger,
    ) {
        inline fun <reified T : Throwable> status(statusCode: HttpStatusCode) {
            config.exception<T>(formatException(logger, statusCode) { logger.warn(it) { it.message } })
        }
    }

fun Application.configureStatusPages(errorStatuses: ErrorStatuses.() -> Unit = {}) {
    val logger = KotlinLogging.logger {}
    install(StatusPages) {
        ErrorStatuses(this, logger).apply(errorStatuses)

        suspend fun respondError(
            call: ApplicationCall,
            cause: ErrorResponse,
        ) {
            if (call.response.isSent) return logCommitted(logger, call, cause)
            call.respond(
                cause.status,
                ErrorBody(cause.error, cause.status.value, cause.details),
            )
        }
        exception<ErrorResponse> { call, cause -> respondError(call, cause) }

        val serverError =
            formatException<Throwable>(logger, HttpStatusCode.InternalServerError) { logger.error(it) { it.message } }
        exception<Throwable> { call, cause ->
            // The client hung up while the response was written, which a player does on every seek and skip.
            // Nothing failed on the server, and nobody is left to send an error to.
            if (generateSequence(cause) { it.cause }.any { it is ChannelWriteException }) {
                logger.debug {
                    "Client closed the connection during ${call.request.httpMethod.value} ${call.request.path()}"
                }
            } else {
                serverError(call, cause)
            }
        }
        val badRequest = formatException<BadRequestException>(logger, HttpStatusCode.BadRequest)
        exception<BadRequestException> { call, cause ->
            // Ktor builds a resource while it routes the call, so a resource that refuses its parameters with an
            // ErrorResponse arrives wrapped in a BadRequestException that would hide the reason
            val refusal = generateSequence<Throwable>(
                cause,
            ) { it.cause }.filterIsInstance<ErrorResponse>().firstOrNull()
            if (refusal != null) respondError(call, refusal) else badRequest(call, cause)
        }
        exception<MissingRequestParameterException>(formatException(logger, HttpStatusCode.BadRequest))
        exception<ParameterConversionException>(formatException(logger, HttpStatusCode.BadRequest))
        exception<ContentTransformationException>(formatException(logger, HttpStatusCode.InternalServerError))
        exception<CannotTransformContentToTypeException>(formatException(logger, HttpStatusCode.UnsupportedMediaType))
        exception<UnsupportedMediaTypeException>(formatException(logger, HttpStatusCode.UnsupportedMediaType))

        val statuses = HttpStatusCode.allStatusCodes.filter { it.value >= 400 }.toTypedArray()
        status(*statuses) { statusCode ->
            call.respond(
                statusCode,
                ErrorBody(statusCode.description, statusCode.value),
            )
        }
    }
}
