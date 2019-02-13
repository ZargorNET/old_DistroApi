package net.zargor.distroapi.util.oauth2.impl

import net.zargor.distroapi.util.http.HttpClient
import net.zargor.distroapi.util.oauth2.OAuth2Service

class DiscordOAuth2Service(
    clientId: String,
    clientSecret: String,
    scope: String,
    redirectUri: String,
    httpClient: HttpClient,
    httpAgent: String = "Mozilla/5.0"
) : OAuth2Service(clientId, clientSecret, scope, redirectUri, httpClient, httpAgent) {
    override fun authorizeUrl(): String {
        return "https://discordapp.com/api/oauth2/authorize"
    }

    override fun tokenUrl(): String {
        return "https://discordapp.com/api/oauth2/token"
    }

    override fun revokeTokenUrl(): String {
        return "https://discordapp.com/api/oauth2/token/revoke"
    }
}