package io.dewey.openapi.client.typescript.types

import io.dewey.openapi.client.common.GenerateType
import io.dewey.openapi.client.typescript.TsTypeGenerator
import io.dewey.openapi.common.ClassType
import kotlin.uuid.Uuid

class UUIDTsGenerator : TsTypeGenerator() {
    override fun generateContent(
        classType: ClassType,
        generateSubType: GenerateType<TsType>,
    ): String = "type UUID = `\${string}-\${string}-\${string}-\${string}-\${string}`"

    override fun getParsingMethod(classType: ClassType): TsParseMethod = TsParseMethod.TEXT

    override fun getInsertionMode(classType: ClassType) = TsDataType.COMPLEX

    override fun generateReference(
        classType: ClassType,
        generateSubType: GenerateType<TsType>,
    ): String = "UUID"

    override fun getName(classType: ClassType): String = "UUID"

    override fun canGenerate(classType: ClassType): Boolean = classType.isSubclassOf(Uuid::class)
}
