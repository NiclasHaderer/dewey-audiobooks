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
import kotlin.time.Instant
import kotlin.uuid.Uuid

fun generateJwtPairForUser(
    user: DeweyDatabaseUser,
    config: DeweyAuthConfig<*, *, *>,
): DeweyJwtPair =
    DeweyJwtPair(
        accessToken = generateAccessTokenForUser(user, config),
        refreshToken = generateRefreshTokenForUser(user, config),
    )

internal fun generateAccessTokenForUser(
    user: DeweyDatabaseUser,
    config: DeweyAuthConfig<*, *, *>,
): String {
    val keyPair = config.keyPairs[config.activeKeyId]!!
    val issuer = config.issuer

    return JWT
        .create()
        .withIssuer(issuer)
        .withKeyId(config.activeKeyId)
        .withClaim("sub", user.id.toString())
        .withClaim("type", DeweyJwtTypes.SessionAccess.type)
        .withClaim("ver", user.tokenVersion)
        .withExpiresAt(Date(System.currentTimeMillis() + config.accessTokenExpiryTime))
        .sign(Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey))
}

private const val ACCESS_TOKEN_ID_CLAIM = "tid"

// An access token is a JWT like the one of a login, of its own type and with the id of the row that can revoke it.
fun generateAccessToken(
    user: DeweyDatabaseUser,
    tokenId: Uuid,
    expiresAt: Instant?,
    config: DeweyAuthConfig<*, *, *>,
): String {
    val keyPair = config.keyPairs[config.activeKeyId]!!
    return JWT
        .create()
        .withIssuer(config.issuer)
        .withKeyId(config.activeKeyId)
        .withClaim("sub", user.id.toString())
        .withClaim("type", DeweyJwtTypes.AccessToken.type)
        .withClaim("ver", user.tokenVersion)
        .withClaim(ACCESS_TOKEN_ID_CLAIM, tokenId.toString())
        .apply { if (expiresAt != null) withExpiresAt(Date(expiresAt.toEpochMilliseconds())) }
        .sign(Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey))
}

// Null for every other type of token
internal fun accessTokenId(payload: Payload): Uuid? {
    if (payload.getClaim("type").asString() != DeweyJwtTypes.AccessToken.type) return null
    return payload.getClaim(ACCESS_TOKEN_ID_CLAIM).asString()?.let { runCatching { Uuid.parse(it) }.getOrNull() }
}

internal fun generateRefreshTokenForUser(
    user: DeweyDatabaseUser,
    config: DeweyAuthConfig<*, *, *>,
): String {
    val issuer = config.issuer
    val keyPair = config.keyPairs[config.activeKeyId]!!

    val refreshAge = System.currentTimeMillis() + config.refreshTokenExpiryTime
    return JWT
        .create()
        .withIssuer(issuer)
        .withKeyId(config.activeKeyId)
        .withClaim("type", DeweyJwtTypes.SessionRefresh.type)
        .withClaim("sub", user.id.toString())
        .withClaim("ver", user.tokenVersion)
        .withExpiresAt(Date(refreshAge))
        .sign(Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey))
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
