package io.dewey.openapi.ktor.schema

import io.dewey.openapi.common.ClassType
import io.ktor.http.ContentType
import io.swagger.v3.oas.models.media.DateSchema
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.Schema
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class DateSchemaGenerator : SchemaGenerator() {
    override fun generateSchema(
        classType: ClassType,
        generateSubType: GenerateSchemaSubtype,
    ): Schema<*> = if (classType.clazz == LocalDate::class) DateSchema() else DateTimeSchema()

    override fun canGenerate(classType: ClassType): Boolean = classType.isSubclassOf(LocalDate::class, Instant::class)

    override fun generateContentType(classType: ClassType): ContentType = ContentType.Text.Plain
}
