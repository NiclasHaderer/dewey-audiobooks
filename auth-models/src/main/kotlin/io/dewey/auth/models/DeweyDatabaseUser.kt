package io.dewey.auth.models

import kotlin.uuid.Uuid

// The Internal database model of the user, intentionally does not extend from DeweyUser, so the
// internal database user cannot be accidentally leaked by returning it instead of the DeweyUser

open class DeweyDatabaseUser(
    open val id: Uuid,
    open val username: String,
    open val passwordHash: String,
    open val tokenVersion: Int,
)
