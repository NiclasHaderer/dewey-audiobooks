package io.dewey.auth.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import io.dewey.auth.DeweyAuthConfig
import io.dewey.auth.models.DeweyDatabaseUser
import io.dewey.auth.models.DeweyJwtPair
import io.dewey.auth.models.DeweyJwtTypes
import io.dewey.openapi.ktor.errors.ErrorResponse
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun generateJwtPairForUser(
    user: DeweyDatabaseUser,
    config: DeweyAuthConfig<*, *, *>,
): DeweyJwtPair {
    val now = Clock.System.now()
    return DeweyJwtPair(
        accessToken =
            signJwt(user, DeweyJwtTypes.SessionAccess, now + config.accessTokenExpiryTime.milliseconds, config),
        refreshToken =
            signJwt(user, DeweyJwtTypes.SessionRefresh, now + config.refreshTokenExpiryTime.milliseconds, config),
    )
}

private const val ACCESS_TOKEN_ID_CLAIM = "tid"

// An access token carries the id of the row that can revoke it
internal fun signJwt(
    user: DeweyDatabaseUser,
    type: DeweyJwtTypes,
    expiresAt: Instant?,
    config: DeweyAuthConfig<*, *, *>,
    accessTokenId: Uuid? = null,
): String {
    val keyPair = config.keyPairs[config.activeKeyId]!!
    return JWT
        .create()
        .withIssuer(config.issuer)
        .withKeyId(config.activeKeyId)
        .withClaim("sub", user.id.toString())
        .withClaim("type", type.type)
        .withClaim("ver", user.tokenVersion)
        .apply { if (accessTokenId != null) withClaim(ACCESS_TOKEN_ID_CLAIM, accessTokenId.toString()) }
        .apply { if (expiresAt != null) withExpiresAt(Date(expiresAt.toEpochMilliseconds())) }
        .sign(Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey))
}

// Null for every other type of token
internal fun accessTokenId(payload: Payload): Uuid? {
    if (payload.getClaim("type").asString() != DeweyJwtTypes.AccessToken.type) return null
    return payload.getClaim(ACCESS_TOKEN_ID_CLAIM).asString()?.let { runCatching { Uuid.parse(it) }.getOrNull() }
}

fun validateJwt(
    authConfig: DeweyAuthConfig<*, *, *>,
    token: String,
    type: DeweyJwtTypes,
): DeweyDatabaseUser {
    val decodedJWT = JWT.decode(token)
    if (decodedJWT.algorithm != "RS256") {
        throw ErrorResponse.userError("Unsupported JWT algorithm ${decodedJWT.algorithm}")
    }

    val verifier =
        authConfig.verifierFor(decodedJWT.keyId) ?: throw ErrorResponse.unauthorized("Unknown JWT key id")

    runCatching { verifier.verify(decodedJWT) }
        .onFailure { throw ErrorResponse.unauthorized("Invalid JWT: ${it.message}") }

    return authConfig.userForToken(decodedJWT, type) ?: throw ErrorResponse.unauthorized("JWT is no longer valid")
}

// The user named by a signature-verified token, or null if the type is wrong, the user is gone, or the token
// predates a password change.
internal fun DeweyAuthConfig<*, *, *>.userForToken(
    payload: Payload,
    type: DeweyJwtTypes,
): DeweyDatabaseUser? {
    if (payload.getClaim("type").asString() != type.type) return null
    val userId =
        payload.getClaim("sub").asString()?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: return null
    val user = getUserById(userId) ?: return null
    return user.takeIf { payload.getClaim("ver").asInt() == it.tokenVersion }
}
