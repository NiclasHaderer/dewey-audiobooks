package io.dewey.openapi.client.kotlin.types

import io.dewey.openapi.client.common.GenerateType
import io.dewey.openapi.client.kotlin.KtTypeGenerator
import io.dewey.openapi.common.ClassType
import io.dewey.openapi.common.isPatch
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter

class InterfaceKtGenerator : KtTypeGenerator() {
    override fun generateContent(
        classType: ClassType,
        generateSubType: GenerateType<KtType>,
    ): String {
        val superClasses =
            classType.superClasses
                .filter { it.memberProperties.isNotEmpty() }
                .filterNot { it.clazz == Enum::class }
                .map { generateSubType(it) }

        val ktProperties = interfaceProperties(classType, generateSubType, false)

        val classInterface =
            buildString {
                val interfaceName =
                    generateName(
                        classType = classType,
                        resolveGeneric = false,
                        generateSubType = generateSubType,
                        isImpl = false,
                        includeBounds = true,
                    )
                append("interface $interfaceName")
                if (superClasses.isNotEmpty()) {
                    append(" : ${superClasses.joinToString(", ") { it.reference() }}")
                }
                append(" {\n")
                ktProperties
                    .filter { !it.declaredInSuperclass }
                    .map {
                        append("    ")
                        if (it.overwrites) append("override ")
                        append("val ${it.name}: ${it.type.name}")
                        if (it.type.typeArguments.isNotEmpty()) {
                            append("<${it.type.typeArguments.joinToString(", ")}>")
                        }
                        if (it.nullable) append("?")
                        append("\n")
                    }
                append("}")
            }

        val ktImplProperties = interfaceProperties(classType, generateSubType, true)
        val classInterfaceImpl =
            buildString {
                val dataClassName =
                    generateName(
                        classType = classType,
                        resolveGeneric = false,
                        generateSubType = generateSubType,
                        isImpl = true,
                        includeBounds = true,
                    )
                if (classType.hasPatch) append("@OptIn(ExperimentalSerializationApi::class)\n")
                append("@Serializable\n")
                append("data class $dataClassName(\n")
                ktImplProperties.mapIndexed { i, it ->
                    val propClassType = classType.forMember(it.underlyingProperty)
                    val subType = generateSubType(propClassType)

                    append("    ")
                    if (it.underlyingProperty.isPatch) append("@EncodeDefault(EncodeDefault.Mode.NEVER) ")
                    append("override val ${it.name}: ")
                    if (it.underlyingProperty.returnType.classifier is KTypeParameter) {
                        append(it.type.name)
                    } else {
                        append(subType.nameImpl())
                    }
                    if (it.type.typeArguments.isNotEmpty()) {
                        append("<")
                        val memberType = it.underlyingProperty.returnType
                        // Iterate over the type arguments. If the type argument is something like
                        // `Map<T, BookModel> we do not have to resolve the first type argument,
                        // but we have to resolve the second one
                        memberType.arguments.map {
                            // This is the case if we have a mix of generic and inline generics
                            // e.g., interface Something<T> { val hello: Map<String, T> }
                            val typeName =
                                if (it.type!!.classifier is KClass<*>) {
                                    // This gets called for the inline generics (String) in the example
                                    // above
                                    generateSubType(ClassType.create(it.type!!)).referenceImpl()
                                } else {
                                    // This gets called for the generics (T) in the example above
                                    it.type.toString()
                                }
                            append(typeName)
                            if (it.type!!.isMarkedNullable) append("?")
                        }
                        append(">")
                    }
                    // Fields that may be left out get a default, so callers only name the ones they mean
                    if (it.underlyingProperty.isPatch) {
                        append(" = Patch.Absent")
                    } else if (it.nullable) {
                        append("? = null")
                    }
                    if (i < ktImplProperties.size - 1) append(",\n")
                }

                append("\n")
                val superInterfaceName =
                    generateName(
                        classType = classType,
                        resolveGeneric = false,
                        generateSubType = generateSubType,
                        isImpl = false,
                        includeBounds = false,
                    )
                append(") : $superInterfaceName")
            }

        return classInterface + "\n\n" + classInterfaceImpl
    }

    private val ClassType.hasPatch: Boolean get() = memberProperties.any { it.isPatch }

    override fun getName(classType: ClassType): String = classType.simpleName

    override fun getInsertionMode(classType: ClassType): KtDataType = KtDataType.COMPLEX

    override fun withImports(
        classType: ClassType,
        generateSubType: GenerateType<KtType>,
    ): List<String> {
        val serialization =
            listOfNotNull(
                "import kotlinx.serialization.Serializable",
                "import kotlinx.serialization.EncodeDefault".takeIf { classType.hasPatch },
                "import kotlinx.serialization.ExperimentalSerializationApi".takeIf { classType.hasPatch },
            )
        val members =
            classType.memberProperties.flatMap {
                // If the property is a generic type, we can skip it
                if (classType.isGenericProperty(it)) {
                    emptyList()
                } else {
                    val type = generateSubType(classType.forMember(it))
                    type.imports()
                }
            }
        return (serialization + members).distinct()
    }

    override fun generateReference(
        classType: ClassType,
        generateSubType: GenerateType<KtType>,
    ): String =
        generateName(
            classType = classType,
            resolveGeneric = true,
            generateSubType = generateSubType,
            isImpl = false,
            includeBounds = false,
        )

    override fun priority(classType: ClassType): Int = -10

    override fun canGenerate(classType: ClassType): Boolean = true

    override fun generateImplReference(
        classType: ClassType,
        generateSubType: GenerateType<KtType>,
    ): String =
        generateName(
            classType = classType,
            resolveGeneric = true,
            generateSubType = generateSubType,
            isImpl = true,
            includeBounds = false,
        )

    override fun getImplName(classType: ClassType): String = classType.simpleName + "Impl"

    private fun generateName(
        classType: ClassType,
        resolveGeneric: Boolean,
        generateSubType: GenerateType<KtType>,
        isImpl: Boolean,
        includeBounds: Boolean,
    ): String {
        val typeParams = classType.typeParameters()
        val typeParamsString =
            typeParams
                .joinToString(", ") {
                    if (resolveGeneric) {
                        val typePar = classType.resolveTypeParameter(it)
                        val subType = generateSubType.invoke(typePar)
                        if (isImpl) subType.referenceImpl() else subType.reference()
                    } else {
                        // Check if the generic type has upper bounds
                        val upperBounds = it.upperBounds
                        val bounds =
                            if (upperBounds.isNotEmpty()) {
                                upperBounds
                                    .filter { bound ->
                                        val clazz = bound.classifier as KClass<*>
                                        (clazz == Any::class && bound.isMarkedNullable).not()
                                    }.joinToString(", ") { bound ->
                                        generateSubType(ClassType.create(bound)).reference()
                                    }
                            } else {
                                ""
                            }

                        "${it.name} ${
                            if (bounds.isNotEmpty() && includeBounds) {
                                ": $bounds"
                            } else {
                                ""
                            }
                        }"
                    }
                }.trim()

        return "${classType.simpleName + if (isImpl) "Impl" else ""}${
            if (typeParams.isNotEmpty()) {
                "<$typeParamsString>"
            } else {
                ""
            }
        }"
    }
}
