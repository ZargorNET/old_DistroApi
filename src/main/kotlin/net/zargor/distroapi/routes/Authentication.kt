package net.zargor.distroapi.routes

import com.github.scribejava.apis.DiscordApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service
import com.google.gson.JsonParser
import net.zargor.distro.databasemodels.model.User
import net.zargor.distro.databasemodels.model.Username
import net.zargor.distroapi.DISCORD_API_URL
import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.extension.addDay
import net.zargor.distroapi.extension.json
import net.zargor.distroapi.extension.needsToBeAuthenticated
import net.zargor.distroapi.extension.returnUnauthorized
import spark.kotlin.RouteHandler
import java.util.*

sealed class Authentication {
    companion object {
        val discord: (RouteHandler).() -> Any = get@{
            val code = request.queryParams("code")
            val redirectUri = request.queryParams("redirect_uri")
            if (code.isNullOrBlank() || redirectUri.isNullOrBlank())
                return@get response.json(error = "invalid_request", responseCode = 400)

            val service: OAuth20Service = ServiceBuilder(DistroApi.instance.config.oAuth.discord.clientId)
                .apiSecret(DistroApi.instance.config.oAuth.discord.clientSecret)
                .callback(redirectUri)
                .scope(SCOPE)
                .userAgent("Mozilla/5.0")
                .build(DiscordApi.instance())

            val token: OAuth2AccessToken
            try {
                token = service.getAccessToken(code)
            } catch (e: OAuth2AccessTokenErrorResponse) {
                return@get response.json(error = "code_invalid", responseCode = 400)
            }
            if (token.scope != SCOPE)
                return@get response.json(error = "scope_mismatch", responseCode = 400)

            //GET USER INFO
            val discordUserReq = OAuthRequest(Verb.GET, "$DISCORD_API_URL/users/@me")
            service.signRequest(token, discordUserReq)
            val discordUserRes = service.execute(discordUserReq)
            if (discordUserRes.code != 200) {
                return@get response.json(error = "could_not_fetch_discord_user", responseCode = 400)
            }
            val jsonParser = JsonParser()
            val discordRes = jsonParser.parse(discordUserRes.body).asJsonObject
            val discordUserId = discordRes.get("id").asString
            val discordUserEmail = discordRes.get("email").asString
            val discordUserUsername = discordRes.get("username").asString
            val discordUserDiscriminator = discordRes.get("discriminator").asString
            val discordUserAvatarHash = discordRes.get("avatar").asString

            val user = DistroApi.instance.database.userStorage.getUserByDiscordId(discordUserId)
                ?: User(UUID.randomUUID().toString(), discordUserId)
            user.email = discordUserEmail
            user.username = Username(discordUserUsername, discordUserDiscriminator)
            user.avatarHash = discordUserAvatarHash

            //GENERATE AUTHENTICATION JWT
            val tokenPair = DistroApi.instance.jwt.createToken(user.id, Calendar.getInstance().addDay(14).time)

            user.jwtTokenId = tokenPair.second.jti

            DistroApi.instance.database.userStorage.save(user)

            response.json(data = """{"jwt": {"key": "${tokenPair.first}", "expiresAt": ${tokenPair.second.expiresAt.time}}}""")
        }
        val discordInfo: (RouteHandler).() -> Any = get@{
            return@get response.json(data = """{"id": "${DistroApi.instance.config.oAuth.discord.clientId}", "scopes": "$SCOPE"}""")
        }
        val revoke: (RouteHandler).() -> Any = get@{
            needsToBeAuthenticated(this) { token ->
                val user = DistroApi.instance.database.userStorage.getUserById(token.subject)
                    ?: return@needsToBeAuthenticated response.returnUnauthorized()
                user.jwtTokenId = "-revoked"
                DistroApi.instance.database.userStorage.save(user)
                response.json(data = """{"msg": "ok"}""")
            }
        }

        const val SCOPE = "identify email guilds"
    }
}