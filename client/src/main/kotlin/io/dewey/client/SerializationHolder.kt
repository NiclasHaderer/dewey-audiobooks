package io.dewey.client

import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

@Suppress("UNCHECKED_CAST")
open class SerializationHolder {
    private val serializers = mutableMapOf<KType, BodySerializer<*>>()
    private val deserializers = mutableMapOf<KType, BodyDeserializer<*>>()

    inline fun <reified T> serialize(noinline serializer: BodySerializer<T>) {
        val type = typeOf<T>()
        serialize(type, serializer)
    }

    fun <T> serialize(
        type: KType,
        serializer: BodySerializer<T>,
    ) {
        serializers[type] = serializer
    }

    inline fun <reified T> deserialize(noinline deserializer: BodyDeserializer<T>) {
        val type = typeOf<T>()
        deserialize(type, deserializer)
    }

    fun <T> deserialize(
        type: KType,
        deserializer: BodyDeserializer<T>,
    ) {
        deserializers[type] = deserializer
    }

    inline fun <reified T> getSerializer(): BodySerializer<T>? = getSerializer(typeOf<T>())

    fun <T> getSerializer(type: KType): BodySerializer<T>? = serializers[type] as? BodySerializer<T>?

    inline fun <reified T> getDeserializer(): BodyDeserializer<T>? = getDeserializer(typeOf<T>())

    fun <T> getDeserializer(type: KType): BodyDeserializer<T>? = deserializers[type] as? BodyDeserializer<T>?

    inline fun <reified T> getClosestSerializer(): BodySerializer<T> = getClosestSerializer(typeOf<T>())

    fun <T> getClosestSerializer(type: KType): BodySerializer<T> =
        getSerializer(type) ?: run {
            serializers.entries.first { (key, _) -> type.isSubtypeOf(key) }.value as BodySerializer<T>
        }

    inline fun <reified T> getClosestDeserializer(): BodyDeserializer<T> = getClosestDeserializer(typeOf<T>())

    fun <T> getClosestDeserializer(type: KType): BodyDeserializer<T> =
        getDeserializer(type) ?: run {
            deserializers.entries.first { (key, _) -> type.isSubtypeOf(key) }.value as BodyDeserializer<T>
        }
}
