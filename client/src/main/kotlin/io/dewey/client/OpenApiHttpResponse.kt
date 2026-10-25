package io.dewey.client

import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlin.coroutines.CoroutineContext

class OpenApiHttpResponse<T>(
    private val delegate: HttpResponse,
    private val responseBodyParser: BodyDeserializer<T>,
    private val responseBodyType: TypeInfo,
) : HttpResponse() {
    override val call: HttpClientCall
        get() = delegate.call

    @InternalAPI
    override val rawContent: ByteReadChannel
        get() = delegate.rawContent
    override val requestTime: GMTDate
        get() = delegate.requestTime
    override val responseTime: GMTDate
        get() = delegate.responseTime
    override val status: HttpStatusCode
        get() = delegate.status
    override val version: HttpProtocolVersion
        get() = delegate.version
    override val headers: Headers
        get() = delegate.headers
    override val coroutineContext: CoroutineContext
        get() = delegate.coroutineContext

    suspend fun body(): T = responseBodyParser(this, responseBodyType)

    internal suspend fun error(): InternalApiError = delegate.body<InternalApiError>()
}
