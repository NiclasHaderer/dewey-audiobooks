package io.dewey.openapi.ktor.schema

import io.dewey.openapi.common.ClassType
import io.ktor.http.ContentType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import kotlin.uuid.Uuid

class UUIDSchemaGenerator : SchemaGenerator() {
    override fun generateSchema(
        classType: ClassType,
        generateSubType: GenerateSchemaSubtype,
    ): Schema<*> = StringSchema().format("uuid")

    override fun canGenerate(classType: ClassType): Boolean = classType.isSubclassOf(Uuid::class)

    override fun generateContentType(classType: ClassType): ContentType = ContentType.Text.Plain
}
