import arrow.core.Either
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import java.io.IOException

abstract class `--` {
    // -- Start
    protected open suspend fun <T> wrapInEither(
        cb: suspend () -> OpenApiHttpResponse<T>,
    ): Either<OpenApiHttpResponse<T>, ApiError> =
        runCatching {
            try {
                val response = cb()
                return if (response.status.isSuccess()) {
                    Either.Left(response)
                } else {
                    Either.Right(response.error().wrap(ApiErrorType.HTTP))
                }
            } catch (e: ClientRequestException) {
                return Either.Right(e.response.body<InternalApiError>().wrap(ApiErrorType.HTTP))
            } catch (e: RedirectResponseException) {
                return Either.Right(
                    ApiError(
                        ApiErrorType.HTTP,
                        e.response.status.value,
                        "Redirect",
                        "The server has redirected the request to ${e.response.headers[HttpHeaders.Location]}",
                    ),
                )
            } catch (e: ServerResponseException) {
                return Either.Right(e.response.body<InternalApiError>().wrap(ApiErrorType.HTTP))
            } catch (e: ResponseException) {
                return Either.Right(e.response.body<InternalApiError>().wrap(ApiErrorType.HTTP))
            } catch (e: IOException) {
                return Either.Right(
                    ApiError(
                        ApiErrorType.Connection,
                        0,
                        "IOException",
                        "Could not connect to the server",
                    ),
                )
            } catch (e: Throwable) {
                // Check if the name of the exception is a serialization exception
                // 1. Kotlinx Serialization
                // 2. Jackson
                // 3. Gson

                val serializationClassName = e.javaClass.name
                val isSerializationException =
                    serializationClassName.startsWith("kotlinx.serialization.") ||
                        serializationClassName.startsWith("com.fasterxml.jackson.databind.") ||
                        serializationClassName.startsWith(
                            "com.google.gson.",
                        )

                if (!isSerializationException) throw e

                return Either.Right(
                    ApiError(
                        ApiErrorType.ClientSerialization,
                        0,
                        "SerializationException",
                        "An exception occurred while processing the response",
                    ),
                )
            }
        }.getOrElse {
            val error =
                ApiError(
                    ApiErrorType.Unknown,
                    0,
                    "\"${it.javaClass.simpleName}\" was thrown while processing the response",
                    it.message,
                )
            Either.Right(error)
        }
    // -- End
}
