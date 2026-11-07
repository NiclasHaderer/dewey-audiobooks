package io.dewey.openapi

import io.dewey.openapi.client.typescript.generateTsClient
import io.dewey.openapi.ktor.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TsGenerationTest {
    @Test
    fun testSomething() {
        val generatedStuff =
            generatedFiles { fileWriter ->
                testRoutes()
                generateTsClient(
                    dist = "",
                    fileWriter = fileWriter,
                    apiFactoryName = "createTestApi",
                    cleanDistPackage = false,
                )
            }
        val models =
            mapOf(
                "models/index.ts" to
                    listOf(
                        "empty",
                        "generic-route",
                        "generic-route2",
                        "generic-route3",
                        "list-route",
                        "map-route",
                        "pair",
                        "set-route",
                        "uuid",
                    ).joinToString("") { "export * from \"./$it\";\n" },
                "models/empty.ts" to "export type Empty = \"\"\n",
                "models/generic-route.ts" to
                    """
                    import type {Pair} from "./pair";
                    import type {UUID} from "./uuid";

                    export interface GenericRoute<T extends NonNullable<unknown>> {
                      name: Pair<string, UUID>;
                      someParam: T;
                    }

                    """.trimIndent(),
                "models/generic-route2.ts" to
                    """
                    export interface GenericRoute2<T, U> {
                      name: string;
                      someParam: T;
                      someParam2: U;
                    }

                    """.trimIndent(),
                "models/generic-route3.ts" to
                    """
                    import type {GenericRoute2} from "./generic-route2";

                    export interface GenericRoute3<T> {
                      name: string;
                      someParam: Record<string, T>;
                      someParam2: GenericRoute2<T, string>;
                    }

                    """.trimIndent(),
                "models/list-route.ts" to
                    """
                    import type {UUID} from "./uuid";

                    export interface ListRoute {
                      name: string;
                      someParam: Array<UUID>;
                    }

                    """.trimIndent(),
                "models/map-route.ts" to
                    """
                    import type {UUID} from "./uuid";

                    export interface MapRoute {
                      hint: string | null;
                      label?: string;
                      lookup?: Record<string, UUID> | null;
                      name: boolean;
                      note?: string | null;
                      someParam: Record<string, UUID>;
                    }

                    """.trimIndent(),
                "models/pair.ts" to
                    """
                    export interface Pair<A, B> {
                      first: A;
                      second: B;
                    }

                    """.trimIndent(),
                "models/set-route.ts" to
                    """
                    import type {UUID} from "./uuid";

                    export interface SetRoute {
                      name: number;
                      someParam: Array<UUID>;
                    }

                    """.trimIndent(),
                "models/uuid.ts" to
                    $$"""
                    export type UUID = `${string}-${string}-${string}-${string}-${string}`

                    """.trimIndent(),
            )

        assertEquals(models, generatedStuff.filterKeys { it.startsWith("models/") })

        val apiClient =
            $$"""
            import {ApiCallData, ApiInterceptor, ApiResponse, ApiSocket, ApiSocketData, ApiSocketInterceptor, _request, _createUrl, _mergeHeaders, _socket} from "./client";
            import type {Empty, GenericRoute, GenericRoute2, GenericRoute3, ListRoute, MapRoute, SetRoute, UUID} from "./models";

            export const createTestApi = (
              defaultHeaders: HeadersInit = {},
              defaultInterceptors: ApiInterceptor[] = [],
              executor = (callData: ApiCallData) => fetch(callData.route, {method: callData.method, headers: callData.headers, body: callData.bodySerializer(callData.body)}),
              defaultSocketInterceptors: ApiSocketInterceptor[] = [],
              socketExecutor = (socketData: ApiSocketData) => new WebSocket(socketData.route)
            ) => {
              const defaultHeadersImpl = new Headers(defaultHeaders)
              return {
                apiVersion: "1.2.3",
                listCookies: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<Array<string>>> => {
                  return _request(`/cookies`, "GET", "json", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                listKeys: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<Array<string>>> => {
                  return _request(`/keys`, "GET", "json", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                listShelves: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<Array<string>>> => {
                  return _request(`/shelves`, "GET", "json", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                listen: ({name, someParam, path}: {name: string,someParam: Array<number>,path: UUID}, interceptors: ApiSocketInterceptor[] = []): Promise<ApiSocket<never, ListRoute>> => {
                  return _socket(_createUrl(`/${path}/listen`, {name, someParam}), [...defaultSocketInterceptors, ...interceptors], socketExecutor, false);
                },
                refreshCookie: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<Empty>> => {
                  return _request(`/cookies`, "POST", "text", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                rotateKeys: (headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<Empty>> => {
                  return _request(`/keys`, "POST", "text", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, true);
                },
                v1: ({name, someParam, path}: {name: string,someParam: Array<number>,path: UUID}, headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<ListRoute>> => {
                  return _request(_createUrl(`/${path}/V1`, {name, someParam}), "GET", "json", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                v2: ({name, someParam, path}: {name: string,someParam: Array<number>,path: UUID}, headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<MapRoute>> => {
                  return _request(_createUrl(`/${path}/V2`, {name, someParam}), "GET", "json", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                v3: ({name, someParam, path}: {name: string,someParam: Array<number>,path: UUID}, headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<SetRoute>> => {
                  return _request(_createUrl(`/${path}/V3`, {name, someParam}), "GET", "json", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                v4: ({name, someParam, path}: {name: string,someParam: Array<number>,path: UUID}, headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<GenericRoute<UUID>>> => {
                  return _request(_createUrl(`/${path}/V4`, {name, someParam}), "GET", "json", _mergeHeaders(defaultHeadersImpl, headers), undefined, [...defaultInterceptors, ...interceptors], executor, false);
                },
                v5: ({name, someParam, path}: {name: string,someParam: Array<number>,path: UUID}, body: GenericRoute2<string, UUID>, headers: HeadersInit = {}, interceptors: ApiInterceptor[] = []): Promise<ApiResponse<GenericRoute3<string>>> => {
                  return _request(_createUrl(`/${path}/V5`, {name, someParam}), "POST", "json", _mergeHeaders(defaultHeadersImpl, headers), body, [...defaultInterceptors, ...interceptors], executor, false);
                }
              } as const;
            }
            """.trimIndent()
                .replace("\\{", "{")

        val generatedApiClient = generatedStuff["api-client.ts"]
        assertEquals(apiClient, generatedApiClient)
    }

    @Test
    fun `a model named like an alias the client defines is refused`() {
        lateinit var error: IllegalStateException
        testApplication {
            application {
                openApi()
                routing { get<Routes.V1, UuidClash> { TODO() } }
                error =
                    assertFailsWith<IllegalStateException> {
                        generateTsClient(dist = "", fileWriter = { _, _ -> }, cleanDistPackage = false)
                    }
            }
        }
        assertContains(error.message!!, "UUID")
    }
}

@Serializable
@SerialName("io.dewey.openapi.UUID")
data class UuidClash(
    val count: Int,
)
