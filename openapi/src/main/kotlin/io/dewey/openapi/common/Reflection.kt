package io.dewey.openapi.common

import kotlin.reflect.KClass

@InternalAPI
val KClass<*>.parent: KClass<*>?
    get() = this.java.enclosingClass?.kotlin
