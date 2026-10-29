package io.dewey.server.api

import io.dewey.auth.interactions.issueAccessToken
import io.dewey.auth.interactions.withConfirmedPassword
import io.dewey.models.AccessToken
import io.dewey.models.CreateAccessToken
import io.dewey.models.CreatedAccessToken
import io.dewey.models.PasswordConfirmation
import io.dewey.models.RevealedAccessToken
import io.dewey.models.UpdateAccessToken
import io.dewey.openapi.ktor.delete
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.patch
import io.dewey.openapi.ktor.post
import io.dewey.server.plugins.auth.deweyPrincipal
import io.dewey.server.repositories.AccessTokenRepository
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject

fun Routing.accessTokenRouting() {
    val accessTokens by inject<AccessTokenRepository>()

    get<Api.Auth.User.Tokens, List<AccessToken>> { accessTokens.list(deweyPrincipal().userId) }

    post<Api.Auth.User.Tokens, CreateAccessToken, CreatedAccessToken> { _, create ->
        withConfirmedPassword(create.password) {
            val userId = deweyPrincipal().userId
            val accessToken = accessTokens.create(userId, create)
            CreatedAccessToken(issueAccessToken(userId, accessToken.id, accessToken.expiresAt), accessToken)
        }
    }

    post<Api.Auth.User.Tokens.Id.Reveal, PasswordConfirmation, RevealedAccessToken> { route, confirmation ->
        withConfirmedPassword(confirmation.password) {
            val userId = deweyPrincipal().userId
            val accessToken = accessTokens.get(userId, route.id)
            RevealedAccessToken(issueAccessToken(userId, accessToken.id, accessToken.expiresAt))
        }
    }

    // The token carries only its row id, so a change reaches every copy of it on the next request. The expiry is
    // signed into the token instead and cannot change after the fact.
    patch<Api.Auth.User.Tokens.Id, UpdateAccessToken, AccessToken> { route, update ->
        withConfirmedPassword(update.password) { accessTokens.update(deweyPrincipal().userId, route.id, update) }
    }

    delete<Api.Auth.User.Tokens.Id, PasswordConfirmation, Unit> { route, confirmation ->
        withConfirmedPassword(confirmation.password) { accessTokens.revoke(deweyPrincipal().userId, route.id) }
    }
}
