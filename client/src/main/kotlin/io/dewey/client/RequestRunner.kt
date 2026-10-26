package io.dewey.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.takeFrom
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendEncodedPathSegments
import io.ktor.http.content.NullBody
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.isSecure
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.InternalAPI

typealias OnBeforeRequest<T> = suspend (metadata: RequestMetadata<T>, requestBuilder: HttpRequestBuilder) -> Unit
typealias OnAfterRequest<T, R> = suspend (metadata: RequestMetadata<T>, response: OpenApiHttpResponse<R>) -> Unit

typealias BodySerializer<T> = suspend (builder: HttpRequestBuilder, body: T, typeInfo: TypeInfo) -> Unit
typealias BodyDeserializer<T> = suspend (httpResponse: HttpResponse, typeInfo: TypeInfo) -> T

abstract class RequestRunner(
    protected val baseUrl: Url,
) : SerializationHolder() {
    private val beforeRequestHooks: MutableList<OnBeforeRequest<*>> = mutableListOf()
    private val afterRequestHooks: MutableList<OnAfterRequest<*, *>> = mutableListOf()
    private val requestFailedHooks: MutableList<OnAfterRequest<*, *>> = mutableListOf()
    private val client: HttpClient by lazy { createClient() }

    init {
        serialize<Any> { builder, body, typeInfo ->
            builder.contentType(ContentType.Application.Json)
            builder.setBody(body, typeInfo)
        }
        deserialize<Any> { response, typeInfo ->
            response.body(typeInfo)
        }
    }

    open fun HttpClientConfig<*>.configureClient() {}

    open fun createClient() = HttpClient { configureClient() }

    fun onBeforeRequest(onBeforeRequest: OnBeforeRequest<*>) {
        beforeRequestHooks.add(onBeforeRequest)
    }

    fun onAfterRequest(onAfterRequest: OnAfterRequest<*, *>) {
        afterRequestHooks.add(onAfterRequest)
    }

    fun onRequestFailed(onRequestFailed: OnAfterRequest<*, *>) {
        requestFailedHooks.add(onRequestFailed)
    }

    suspend fun <T, R> makeRequest(
        metadata: RequestMetadata<T>,
        requestBody: TypeInfo,
        responseBody: TypeInfo,
        onBeforeRequest: OnBeforeRequest<T>,
        onAfterRequest: OnAfterRequest<T, R>,
    ): OpenApiHttpResponse<R> {
        val response =
            client.request(urlOf(metadata)) {
                this.method = metadata.method
                val serializer = this@RequestRunner.getClosestSerializer<T>(requestBody.kotlinType!!)
                serializer(this, metadata.body, requestBody)
                metadata.headers.forEach { key, value -> this.headers.appendAll(key, value) }
                onBeforeRequest(metadata, this)
                beforeRequestHooks.forEach { it(metadata, this) }
            }

        val apiResponse =
            OpenApiHttpResponse<R>(
                response,
                this@RequestRunner.getClosestDeserializer(responseBody.kotlinType!!),
                responseBody,
            )
        onAfterRequest(metadata, apiResponse)
        afterRequestHooks.forEach { it(metadata, apiResponse) }
        if (!response.status.isSuccess()) {
            requestFailedHooks.forEach { it(metadata, apiResponse) }
        }
        return apiResponse
    }

    // The client has to have the WebSockets plugin installed, with a contentConverter for the messages
    suspend fun <SEND, RECEIVE> openSocket(
        metadata: RequestMetadata<Unit>,
        sendType: TypeInfo,
        receiveType: TypeInfo,
        onBeforeRequest: OnBeforeRequest<Unit>,
    ): ApiSocket<SEND, RECEIVE> {
        val request =
            HttpRequestBuilder().apply {
                url.takeFrom(urlOf(metadata))
                url.protocol = if (url.protocol.isSecure()) URLProtocol.WSS else URLProtocol.WS
                metadata.headers.forEach { key, value -> headers.appendAll(key, value) }
            }
        onBeforeRequest(metadata, request)
        beforeRequestHooks.forEach { it(metadata, request) }
        return ApiSocket(client.webSocketSession { takeFrom(request) }, sendType, receiveType)
    }

    private fun urlOf(metadata: RequestMetadata<*>): Url =
        URLBuilder(baseUrl)
            .appendEncodedPathSegments(metadata.path)
            .apply {
                metadata.queryParameters.forEach { (name, value) ->
                    if (value != null) parameters.append(name, value.toString())
                }
            }.build()
}

@OptIn(InternalAPI::class)
private fun <T> HttpRequestBuilder.setBody(
    body: T,
    typeInfo: TypeInfo,
) {
    when (body) {
        null -> {
            this.body = NullBody
            bodyType = typeInfo
        }

        is OutgoingContent -> {
            this.body = body
            bodyType = null
        }

        else -> {
            this.body = body
            bodyType = typeInfo
        }
    }
}
