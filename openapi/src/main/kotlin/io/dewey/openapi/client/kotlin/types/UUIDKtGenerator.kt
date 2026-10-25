package io.dewey.openapi.client.kotlin.types

import io.dewey.openapi.client.common.GenerateType
import io.dewey.openapi.client.kotlin.KtTypeGenerator
import io.dewey.openapi.common.ClassType
import kotlin.uuid.Uuid

class UUIDKtGenerator : KtTypeGenerator() {
    override fun generateContent(
        classType: ClassType,
        generateSubType: GenerateType<KtType>,
    ): String = "Uuid"

    override fun getInsertionMode(classType: ClassType) = KtDataType.PRIMITIVE

    override fun getName(classType: ClassType): String? = null

    override fun generateReference(
        classType: ClassType,
        generateSubType: GenerateType<KtType>,
    ): String? = null

    override fun withImports(
        classType: ClassType,
        generateSubType: GenerateType<KtType>,
    ): List<String> = listOf("import kotlin.uuid.Uuid")

    override fun canGenerate(classType: ClassType): Boolean = classType.isSubclassOf(Uuid::class)
}
