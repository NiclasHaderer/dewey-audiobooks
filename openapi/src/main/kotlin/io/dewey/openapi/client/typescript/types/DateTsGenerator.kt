package io.dewey.openapi.client.typescript.types

import io.dewey.openapi.client.common.GenerateType
import io.dewey.openapi.client.typescript.TsTypeGenerator
import io.dewey.openapi.common.ClassType
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

class DateTsGenerator : TsTypeGenerator() {
    override fun generateContent(
        classType: ClassType,
        generateSubType: GenerateType<TsType>,
    ): String = "string"

    override fun getName(classType: ClassType): String? = null

    override fun getParsingMethod(classType: ClassType): TsParseMethod = TsParseMethod.JSON

    override fun getInsertionMode(classType: ClassType) = TsDataType.PRIMITIVE

    override fun generateReference(
        classType: ClassType,
        generateSubType: GenerateType<TsType>,
    ): String? = null

    override fun canGenerate(classType: ClassType): Boolean = classType.isSubclassOf(LocalDate::class, Instant::class)
}
