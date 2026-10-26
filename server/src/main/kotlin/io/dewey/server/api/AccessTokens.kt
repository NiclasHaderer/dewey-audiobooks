package io.dewey.server.api

import io.dewey.auth.interactions.issueAccessToken
import io.dewey.auth.utils.requireLoginSession
import io.dewey.models.AccessToken
import io.dewey.models.CreateAccessToken
import io.dewey.models.CreatedAccessToken
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

    // A token that could mint tokens could outlive its own revocation through them
    post<Api.Auth.User.Tokens, CreateAccessToken, CreatedAccessToken> { _, create ->
        val principal = deweyPrincipal()
        principal.requireLoginSession("create an access token")
        val accessToken = accessTokens.create(principal.userId, create)
        CreatedAccessToken(issueAccessToken(principal.userId, accessToken.id, accessToken.expiresAt), accessToken)
    }

    // A token may revoke itself, which is how a client logs out, but no other token
    delete<Api.Auth.User.Tokens.Id, Unit, Unit> { route, _ ->
        val principal = deweyPrincipal()
        if (principal.accessTokenId != route.tokenId) principal.requireLoginSession("revoke another access token")
        accessTokens.revoke(principal.userId, route.tokenId)
    }
}
