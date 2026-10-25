package io.dewey.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.dewey.auth.models.DeweyDatabaseUser
import io.dewey.auth.models.DeweyJwtTypes
import io.dewey.auth.models.DeweyRegisteredUser
import io.dewey.auth.utils.DeweyPrincipal
import io.dewey.auth.utils.userForToken
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.util.AttributeKey
import java.security.KeyPair
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

internal typealias KeyId = String

internal typealias GetPrincipal =
    ApplicationCall.(user: DeweyDatabaseUser, setError: (error: JwtError) -> Unit) -> DeweyPrincipal?

internal typealias AuthHeaderProvider = (call: ApplicationCall) -> HttpAuthHeader?

// Falls back to a cookie so browser-native requests (<img src>, <audio src>) that can't set headers still authenticate.
fun bearerFromHeaderOrCookie(cookieName: String): AuthHeaderProvider =
    { call ->
        runCatching { call.request.parseAuthorizationHeader() }.getOrNull()
            ?: call.request.cookies[cookieName]?.let { HttpAuthHeader.Single("Bearer", it) }
    }

internal val PLUGIN_CONFIG_KEY = AttributeKey<DeweyAuthConfig<*, *>>("DeweyAuthPlugin")

internal fun <PERMISSIONS, UPDATE_PERMISSIONS> RoutingContext.deweyAuthConfig():
    DeweyAuthConfig<PERMISSIONS, UPDATE_PERMISSIONS> {
    if (!call.attributes.contains(PLUGIN_CONFIG_KEY)) {
        throw IllegalStateException("DeweyAuthPlugin not installed")
    }

    @Suppress("UNCHECKED_CAST")
    return call.attributes[PLUGIN_CONFIG_KEY] as DeweyAuthConfig<PERMISSIONS, UPDATE_PERMISSIONS>
}

data class JwtError(
    val error: String,
    val statusCode: HttpStatusCode,
)

private val JWT_VALIDATION_FAILED = AttributeKey<JwtError>("JWT_VALIDATION_FAILED")

class DeweyAuthConfig<PERMISSIONS, UPDATE_PERMISSIONS>(
    val ssl: Boolean = true,
    val firstUserIsAdmin: Boolean = true,
    val realm: String? = null,
    val accessTokenExpiryTime: Long,
    val refreshTokenExpiryTime: Long,
    val keyPairs: Map<KeyId, KeyPair>,
    val activeKeyId: KeyId,
    val issuer: String,
    val guards: Map<String, GetPrincipal>,
    val guardAuthHeaders: Map<String, AuthHeaderProvider>,
    val getUserByUsername: (username: String) -> DeweyDatabaseUser?,
    val allowNewSignups: () -> Boolean,
    val getUserById: (id: Uuid) -> DeweyDatabaseUser?,
    val isFirstUser: () -> Boolean,
    val createUser: (registeredUser: DeweyRegisteredUser) -> DeweyDatabaseUser,
    val listAllUsers: () -> List<DeweyDatabaseUser>,
    val deleteUser: (user: DeweyDatabaseUser) -> Unit,
    val renameUser: (user: DeweyDatabaseUser, newName: String) -> DeweyDatabaseUser,
    val updatePassword: (user: DeweyDatabaseUser, newPassword: String) -> DeweyDatabaseUser,
    val updateUserPermissions: (user: DeweyDatabaseUser, newPermissions: UPDATE_PERMISSIONS) -> DeweyDatabaseUser,
    val passwordMeetsRequirements: (password: String) -> Pair<Boolean, String?>,
    val usernameMeetsRequirements: (username: String) -> Pair<Boolean, String?>,
    val getUserPermissions: RoutingContext.(user: DeweyDatabaseUser) -> PERMISSIONS,
    private val isAdminUser: (user: DeweyDatabaseUser) -> Boolean,
) {
    // The signing keys are in memory, so tokens are verified directly instead of going through the JWKS endpoint
    internal fun verifierFor(keyId: KeyId?): JWTVerifier? {
        val keyPair = keyPairs[keyId] ?: return null
        return JWT
            .require(Algorithm.RSA256(keyPair.public as RSAPublicKey, null))
            .withIssuer(issuer)
            .acceptLeeway(3)
            .build()
    }

    internal fun Application.applyGuards() {
        install(Authentication) {
            guards.forEach { (guard, getPrincipal) ->
                jwt(guard) {
                    if (this@DeweyAuthConfig.realm != null) {
                        realm = this@DeweyAuthConfig.realm
                    }

                    guardAuthHeaders[guard]?.let { provider -> authHeader { call -> provider(call) } }

                    verifier { authHeader ->
                        val token = (authHeader as? HttpAuthHeader.Single)?.blob ?: return@verifier null
                        verifierFor(runCatching { JWT.decode(token).keyId }.getOrNull())
                    }

                    validate { jwtCredential ->
                        val user =
                            userForToken(jwtCredential.payload, DeweyJwtTypes.Access) ?: run {
                                attributes.put(
                                    JWT_VALIDATION_FAILED,
                                    JwtError("JWT is not valid", HttpStatusCode.Unauthorized),
                                )
                                return@validate null
                            }
                        getPrincipal(user) { error -> attributes.put(JWT_VALIDATION_FAILED, error) }
                    }

                    challenge { _, _ ->
                        val error = call.attributes.getOrNull(JWT_VALIDATION_FAILED)
                        call.respond(
                            error?.statusCode ?: HttpStatusCode.Unauthorized,
                            mapOf("error" to (error?.error ?: "Unknown JWT error")),
                        )
                    }
                }
            }
        }
    }

    internal fun isAdmin(principal: DeweyPrincipal): Boolean {
        return isAdminUser(getUserById(principal.userId) ?: return false)
    }
}

class DeweyAuthConfigBuilder<PERMISSIONS, UPDATE_PERMISSIONS> {
    var ssl = true

    // Default user settings
    var firstUserIsAdmin = true
    var realm: String? = null

    // Token expiry times
    var accessTokenExpiryTime = TimeUnit.DAYS.toMillis(1)

    // Slides forward on every refresh, so only a client that goes quiet for the whole window has to log in again
    var refreshTokenExpiryTime = TimeUnit.DAYS.toMillis(30)

    // Key pairs
    val keyPairs: MutableMap<KeyId, KeyPair> = mutableMapOf()
    lateinit var activeKeyId: KeyId

    // Application paths
    lateinit var issuer: String

    // Functionality
    private lateinit var getUserByUsername: (username: String) -> DeweyDatabaseUser?
    private lateinit var allowNewSignups: () -> Boolean
    private lateinit var getUserById: (id: Uuid) -> DeweyDatabaseUser?
    private lateinit var isFirstUser: () -> Boolean
    private lateinit var createUser: (registeredUser: DeweyRegisteredUser) -> DeweyDatabaseUser
    private lateinit var listAllUsers: () -> List<DeweyDatabaseUser>
    private lateinit var deleteUser: (user: DeweyDatabaseUser) -> Unit
    private lateinit var renameUser: (user: DeweyDatabaseUser, newName: String) -> DeweyDatabaseUser
    private lateinit var updatePassword: (user: DeweyDatabaseUser, newPassword: String) -> DeweyDatabaseUser
    private lateinit var updateUserPermissions:
        (user: DeweyDatabaseUser, newPermissions: UPDATE_PERMISSIONS) -> DeweyDatabaseUser
    private lateinit var getUserPermissions: RoutingContext.(user: DeweyDatabaseUser) -> PERMISSIONS
    private var passwordMeetsRequirements: (password: String) -> Pair<Boolean, String?> = { password ->
        if (password.length < 6) {
            Pair(false, "Password must be at least 6 characters long")
        } else {
            Pair(true, null)
        }
    }
    private var usernameMeetsRequirements: (username: String) -> Pair<Boolean, String?> = { username ->
        if (username.length < 3) {
            Pair(false, "Username must be at least 3 characters long")
        } else if (!username.matches(Regex("^[a-zA-Z0-9_-]*$"))) {
            Pair(false, "Username must be alphanumeric, including - and _")
        } else {
            Pair(true, null)
        }
    }
    private lateinit var isAdminUser: (user: DeweyDatabaseUser) -> Boolean

    // Guards
    private val guards = mutableMapOf<String, GetPrincipal>()
    private val guardAuthHeaders = mutableMapOf<String, AuthHeaderProvider>()

    fun configureGuard(
        guard: String,
        authHeader: AuthHeaderProvider? = null,
        getPrincipal: GetPrincipal,
    ) {
        guards[guard] = getPrincipal
        if (authHeader != null) guardAuthHeaders[guard] = authHeader
    }

    // Builder
    fun build(): DeweyAuthConfig<PERMISSIONS, UPDATE_PERMISSIONS> {
        keyPairs.values.forEach { keyPair ->
            require(keyPair.public.algorithm == "RSA") { "KeyPair ${keyPair.public} is not a RSA key pair" }
        }
        require(keyPairs.isNotEmpty()) { "No key pairs configured" }
        require(keyPairs.containsKey(activeKeyId)) { "Active key ID '$activeKeyId' not found in key pairs" }

        return DeweyAuthConfig(
            ssl = ssl,
            firstUserIsAdmin = firstUserIsAdmin,
            realm = realm,
            accessTokenExpiryTime = accessTokenExpiryTime,
            refreshTokenExpiryTime = refreshTokenExpiryTime,
            keyPairs = keyPairs,
            activeKeyId = activeKeyId,
            issuer = issuer,
            guards = guards.toMap(),
            guardAuthHeaders = guardAuthHeaders.toMap(),
            getUserByUsername = getUserByUsername,
            allowNewSignups = allowNewSignups,
            getUserById = getUserById,
            isFirstUser = isFirstUser,
            createUser = createUser,
            listAllUsers = listAllUsers,
            deleteUser = deleteUser,
            renameUser = renameUser,
            updatePassword = updatePassword,
            updateUserPermissions = updateUserPermissions,
            passwordMeetsRequirements = passwordMeetsRequirements,
            usernameMeetsRequirements = usernameMeetsRequirements,
            getUserPermissions = getUserPermissions,
            isAdminUser = isAdminUser,
        )
    }

    // Operations
    fun getUserByUsername(getUserByUsername: (username: String) -> DeweyDatabaseUser?) {
        this.getUserByUsername = getUserByUsername
    }

    fun allowNewSignups(allowNewSignups: () -> Boolean) {
        this.allowNewSignups = allowNewSignups
    }

    fun isAdminUser(isAdminUser: (user: DeweyDatabaseUser) -> Boolean) {
        this.isAdminUser = isAdminUser
    }

    fun getUserById(getUserById: (id: Uuid) -> DeweyDatabaseUser?) {
        this.getUserById = getUserById
    }

    fun isFirstUser(isFirstUser: () -> Boolean) {
        this.isFirstUser = isFirstUser
    }

    fun createUser(createUser: (registeredUser: DeweyRegisteredUser) -> DeweyDatabaseUser) {
        this.createUser = createUser
    }

    fun listAllUsers(listAllUsers: () -> List<DeweyDatabaseUser>) {
        this.listAllUsers = listAllUsers
    }

    fun deleteUser(deleteUser: (user: DeweyDatabaseUser) -> Unit) {
        this.deleteUser = deleteUser
    }

    fun renameUser(renameUser: (user: DeweyDatabaseUser, newName: String) -> DeweyDatabaseUser) {
        this.renameUser = renameUser
    }

    fun updatePassword(updatePassword: (user: DeweyDatabaseUser, newPassword: String) -> DeweyDatabaseUser) {
        this.updatePassword = updatePassword
    }

    fun updateUserPermissions(
        updateUserPermissions: (user: DeweyDatabaseUser, newPermissions: UPDATE_PERMISSIONS) -> DeweyDatabaseUser,
    ) {
        this.updateUserPermissions = updateUserPermissions
    }

    fun getUserPermissions(getUserPermissions: RoutingContext.(user: DeweyDatabaseUser) -> PERMISSIONS) {
        this.getUserPermissions = getUserPermissions
    }

    fun passwordMeetsRequirements(passwordMeetsRequirements: (password: String) -> Pair<Boolean, String?>) {
        this.passwordMeetsRequirements = passwordMeetsRequirements
    }

    fun usernameMeetsRequirements(usernameMeetsRequirements: (username: String) -> Pair<Boolean, String?>) {
        this.usernameMeetsRequirements = usernameMeetsRequirements
    }
}
