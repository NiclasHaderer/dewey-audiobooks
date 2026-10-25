package io.dewey.server

import io.dewey.client.DeweyClientImpl
import io.dewey.openapi.serializion.kotlin.OpenApiJson
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.setCookie
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.serializer
import kotlin.test.assertNotNull

class TestDeweyClient(
    private val http: HttpClient,
) : DeweyClientImpl(Url("http://localhost")) {
    override fun createClient() = http

    init {
        serialize<Any> { builder, body, typeInfo ->
            builder.contentType(ContentType.Application.Json)
            builder.setBody(
                OpenApiJson.encodeToString(OpenApiJson.serializersModule.serializer(typeInfo.kotlinType!!), body),
            )
        }
        deserialize<Any> { response, typeInfo ->
            if (typeInfo.type == Unit::class) {
                Unit
            } else {
                OpenApiJson.decodeFromString(
                    OpenApiJson.serializersModule.serializer(typeInfo.kotlinType!!),
                    response.bodyAsText(),
                ) as Any
            }
        }
    }
}

val ApplicationTestBuilder.api: TestDeweyClient get() = TestDeweyClient(client)

fun bearer(token: String): Headers = Headers.build { append(HttpHeaders.Authorization, "Bearer $token") }

fun cookie(
    name: String,
    value: String,
): Headers = Headers.build { append(HttpHeaders.Cookie, "$name=$value") }

fun HttpResponse.authCookie(name: String) =
    assertNotNull(
        setCookie().firstOrNull {
            it.name == name
        },
        "no $name cookie",
    )
