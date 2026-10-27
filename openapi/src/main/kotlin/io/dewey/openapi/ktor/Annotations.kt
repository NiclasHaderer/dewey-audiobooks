package io.dewey.openapi.ktor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class Secured(
    val name: String,
    val method: String = "",
)

@Target(AnnotationTarget.CLASS)
annotation class NotSecured

@Target(AnnotationTarget.CLASS)
annotation class Tagged(
    val name: String,
    val description: String = "",
)

// A SerialInfo annotation reaches the descriptor, which is where the schema generators read it from
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Description(
    val description: String,
)

@Target(AnnotationTarget.CLASS)
@Repeatable
annotation class Summary(
    val summary: String,
    val method: String,
    val status: Int = INFER_STATUS,
    val description: String = "",
    val command: String = "",
) {
    companion object {
        const val INFER_STATUS = 0
    }
}
