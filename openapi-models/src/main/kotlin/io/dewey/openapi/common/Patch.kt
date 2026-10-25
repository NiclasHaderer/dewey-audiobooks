package io.dewey.openapi.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val PATCH_SERIAL_NAME = "io.dewey.openapi.common.Patch"

// A request field that may be left out. Absent is a key the client did not send, Set carries what it sent, which is
// null only where T allows one.
@Serializable(with = PatchSerializer::class)
sealed interface Patch<out T> {
    data object Absent : Patch<Nothing>

    data class Set<out T>(
        val value: T,
    ) : Patch<T>
}

class PatchSerializer<T>(
    private val valueSerializer: KSerializer<T>,
) : KSerializer<Patch<T>> {
    // The schema generators find a Patch by this name, the value's own descriptor is its only element
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor(PATCH_SERIAL_NAME, SerialKind.CONTEXTUAL) { element("value", valueSerializer.descriptor) }

    override fun serialize(
        encoder: Encoder,
        value: Patch<T>,
    ) = when (value) {
        is Patch.Set -> encoder.encodeSerializableValue(valueSerializer, value.value)

        Patch.Absent -> throw SerializationException(
            "Patch.Absent cannot be written, annotate the property with @EncodeDefault(EncodeDefault.Mode.NEVER)",
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): Patch<T> {
        if (!valueSerializer.descriptor.isNullable && !decoder.decodeNotNullMark()) {
            throw SerializationException("null is not allowed here, leave the key out instead")
        }
        return Patch.Set(decoder.decodeSerializableValue(valueSerializer))
    }
}

// Leaves the key out for null, so it can never send an explicit null: that takes Patch.Set(null)
fun <T : Any> T?.orAbsent(): Patch<T> = if (this == null) Patch.Absent else Patch.Set(this)

val Patch<*>.isSet: Boolean get() = this is Patch.Set

fun <T> Patch<T>.orElse(default: T): T = if (this is Patch.Set) value else default

inline fun <T> Patch<T>.ifSet(block: (T) -> Unit) {
    if (this is Patch.Set) block(value)
}

inline fun <T, R> Patch<T>.map(transform: (T) -> R): Patch<R> =
    if (this is Patch.Set) Patch.Set(transform(value)) else Patch.Absent
