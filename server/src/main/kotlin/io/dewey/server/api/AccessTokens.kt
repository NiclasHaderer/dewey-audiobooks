package io.dewey.server.api

import io.dewey.auth.interactions.confirmPassword
import io.dewey.auth.interactions.issueAccessToken
import io.dewey.auth.utils.requireLoginSession
import io.dewey.models.AccessToken
import io.dewey.models.CreateAccessToken
import io.dewey.models.CreatedAccessToken
import io.dewey.models.RevealAccessToken
import io.dewey.models.RevealedAccessToken
import io.dewey.openapi.ktor.delete
import io.dewey.openapi.ktor.get
import io.dewey.openapi.ktor.post
import io.dewey.server.plugins.auth.deweyPrincipal
import io.dewey.server.repositories.AccessTokenRepository
import io.ktor.server.routing.Routing
import org.koin.ktor.ext.inject

fun Routing.accessTokenRouting() {
    val accessTokens by inject<AccessTokenRepository>()

    get<Api.Auth.User.Tokens, List<AccessToken>> { accessTokens.list(deweyPrincipal().userId) }

    post<Api.Auth.User.Tokens, CreateAccessToken, CreatedAccessToken> { _, create ->
        confirmPassword(create.password, "create an access token")
        val userId = deweyPrincipal().userId
        val accessToken = accessTokens.create(userId, create)
        CreatedAccessToken(issueAccessToken(userId, accessToken.id, accessToken.expiresAt), accessToken)
    }

    post<Api.Auth.User.Tokens.Id.Reveal, RevealAccessToken, RevealedAccessToken> { route, reveal ->
        confirmPassword(reveal.password, "show an access token")
        val userId = deweyPrincipal().userId
        val accessToken = accessTokens.get(userId, route.tokenId)
        RevealedAccessToken(issueAccessToken(userId, accessToken.id, accessToken.expiresAt))
    }

    delete<Api.Auth.User.Tokens.Id, Unit, Unit> { route, _ ->
        val principal = deweyPrincipal()
        principal.requireLoginSession("revoke an access token")
        accessTokens.revoke(principal.userId, route.tokenId)
    }
}
