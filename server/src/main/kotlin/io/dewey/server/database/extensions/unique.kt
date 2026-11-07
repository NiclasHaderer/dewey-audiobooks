package io.dewey.server.database.extensions

import io.dewey.openapi.ktor.errors.ErrorResponse
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

// Every write inside the block must have one unique constraint a user can hit, or the error names the wrong one
fun <T> rejectDuplicate(
    error: String,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: ExposedSQLException) {
        if ((e.cause as? SQLiteException)?.resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) {
            throw ErrorResponse.conflict(error)
        }
        throw e
    }
