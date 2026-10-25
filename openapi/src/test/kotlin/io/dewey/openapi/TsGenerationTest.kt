package io.dewey.openapi

import io.dewey.openapi.client.typescript.generateTsClient
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class TsGenerationTest {
    @Test
    fun testSomething() {
        val generatedStuff = mutableMapOf<String, String>()
        testApplication {
            application {
                testRoutes()
                generateTsClient(
                    dist = "",
                    fileWriter = { file, content -> generatedStuff[file.name] = content },
                    apiFactoryName = "createTestApi",
                    // The writer above keeps everything in memory, so there is no dist to clean.
                    cleanDistPackage = false,
                )
            }
        }
        val models =
            """
            /* eslint-disable */
            // @ts-nocheck
            import type { Pair } from "./utility-types";

            export interface Something {
              name: string;
            }
            
            export type UUID = `$\{string}-$\{string}-$\{string}-$\{string}-$\{string}`
            
            export interface ListRoute extends Something {
              override name: string;
              someParam: Array<UUID>;
            }
            
            export interface MapRoute {
              hint: string | null;
              label?: string;
              lookup?: Record<string, UUID> | null;
              name: boolean;
              note?: string | null;
              someParam: Record<string, UUID>;
            }
            
            export interface SetRoute {
              name: number;
              someParam: Array<UUID>;
            }
            
            export interface GenericRoute<T> {
              name: Pair<any, UUID>;
              someParam: T;
            }
            
            export interface GenericRoute2<T , U> {
              name: string;
              someParam: T;
              someParam2: U;
            }
            
            export interface GenericRoute3<T> {
              name: string;
              someParam: Record<string, T>;
              someParam2: GenericRoute2<T, string>;
            }
            """.trimIndent()
                .replace("\\{", "{")

        val generatedModels = generatedStuff["models.ts"]
        assertEquals(models, generatedModels)

        val apiClient =
            $$"""
            /* eslint-disable */
            // noinspection JSUnusedGlobalSymbols,ES6UnusedImports
            // noinspection ES6UnusedImports
            // @ts-nocheck
            import {ApiCallData, ApiInterceptor, ApiResponse, ApiSocket, ApiSocketData, ApiSocketInterceptor, _request, _createUrl, _mergeHeaders, _socket} from "./client";
            import type {GenericRoute, GenericRoute2, GenericRoute3, ListRoute, MapRoute, SetRoute, Something, UUID} from "./models";

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
                },
                listen: ({name, someParam, path}: {name: string,someParam: Array<number>,path: UUID}, interceptors: ApiSocketInterceptor[] = []): Promise<ApiSocket<never, ListRoute>> => {
                  return _socket(_createUrl(`/${path}/listen`, {name, someParam}), [...defaultSocketInterceptors, ...interceptors], socketExecutor, false);
                }
              } as const;
            }
            """.trimIndent()
                .replace("\\{", "{")

        val generatedApiClient = generatedStuff["api-client.ts"]
        assertEquals(apiClient, generatedApiClient)
    }
}
