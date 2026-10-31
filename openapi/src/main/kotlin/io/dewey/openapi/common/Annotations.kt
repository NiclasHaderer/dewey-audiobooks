package io.dewey.openapi.common

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

@OptIn(InternalAPI::class)
inline fun <reified T : Annotation> KClass<*>.findAnnotationsFirstUp(): List<T> {
    var root: KClass<*>? = this
    var annotations = this.findAnnotations<T>()
    while (root != null && annotations.isEmpty()) {
        annotations = root.findAnnotations()
        root = root.parent
    }

    return annotations
}
