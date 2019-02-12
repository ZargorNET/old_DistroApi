package net.zargor.distroapi.routes

import com.github.scribejava.apis.DiscordApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuth2AccessTokenErrorResponse
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service
import com.google.gson.JsonParser
import io.javalin.Context
import net.zargor.distro.databasemodels.model.Guild
import net.zargor.distro.databasemodels.model.User
import net.zargor.distro.databasemodels.model.Username
import net.zargor.distroapi.Authentication
import net.zargor.distroapi.DISCORD_API_URL
import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.extension.addDay
import net.zargor.distroapi.extension.resultJson
import java.util.*

sealed class Authentication {
    companion object {
        private data class UserGuild(val id: String, val iconHash: String, val name: String)

        val discord: Context.() -> Unit = get@{
            val code = this.queryParam("code")
            val redirectUri = this.queryParam("redirect_uri")
            if (code.isNullOrBlank() || redirectUri.isNullOrBlank()) {
                this.resultJson(error = "invalid_request").status(400)
                return@get
            }

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
                this.resultJson(error = "code_invalid").status(400)
                return@get
            }
            if (token.scope != SCOPE) {
                this.resultJson(error = "scope_mismatch").status(400)
                return@get
            }

            //GET USER INFO
            val discordUserReq = OAuthRequest(Verb.GET, "$DISCORD_API_URL/users/@me")
            service.signRequest(token, discordUserReq)
            val discordUserRes = service.execute(discordUserReq)
            if (discordUserRes.code != 200) {
                this.resultJson(error = "could_not_fetch_discord_user").status(400)
                return@get
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


            //GET GUILDS
            val discordGuildReq = OAuthRequest(Verb.GET, "$DISCORD_API_URL/users/@me/guilds")
            service.signRequest(token, discordGuildReq)
            val discordGuildRes = service.execute(discordGuildReq)
            if (discordGuildRes.code != 200) {
                this.resultJson(error = "could_not_fetch_discord_user_guilds").status(400)
                return@get
            }
            val guilds: MutableList<UserGuild> = mutableListOf()

            val guildDiscordRes = jsonParser.parse(discordGuildRes.body).asJsonArray
            for (guild in guildDiscordRes) {
                val guildOwner = guild.asJsonObject.get("owner").asBoolean
                if (!guildOwner)
                    continue

                val guildId = guild.asJsonObject.get("id").asString
                val guildName = guild.asJsonObject.get("name").asString
                val guildIconHash = guild.asJsonObject.get("icon")

                val guildIconHashString: String

                if (guildIconHash != null && !guildIconHash.isJsonNull)
                    guildIconHashString = guildIconHash.asString
                else
                    guildIconHashString = "null"

                guilds.add(UserGuild(guildId, guildIconHashString, guildName))
            }

            val dbGuilds = DistroApi.instance.database.guildStorage.getGuildsByOwnerId(user.id).toMutableList()
            guilds.forEach {
                if (dbGuilds.none { g -> g.discordId == it.id })
                    dbGuilds.add(
                        Guild(
                            UUID.randomUUID().toString(),
                            it.id,
                            user.id,
                            mutableListOf(),
                            it.name,
                            it.iconHash
                        )
                    )
                else
                    dbGuilds.removeIf { g -> g.discordId == it.id }
            }

            println(dbGuilds)

            dbGuilds.forEach {
                DistroApi.instance.database.guildStorage.save(it)
            }


            //GENERATE AUTHENTICATION JWT
            val tokenPair = DistroApi.instance.jwt.createToken(user.id, Calendar.getInstance().addDay(14).time)

            user.jwtTokenId = tokenPair.second.jti

            DistroApi.instance.database.userStorage.save(user)

            this.resultJson(data = """{"jwt": {"key": "${tokenPair.first}", "expiresAt": ${tokenPair.second.expiresAt.time}}}""")
        }
        val discordInfo: Context.() -> Unit = {
            this.resultJson(data = """{"id": "${DistroApi.instance.config.oAuth.discord.clientId}", "scopes": "$SCOPE"}""")
        }

        val revoke: Context.() -> Unit = {
            Authentication.needsToBeAuthenticated(this) { user ->
                user.jwtTokenId = "-revoked"
                DistroApi.instance.database.userStorage.save(user)
                this.resultJson(data = """{"msg": "ok"}""")
            }
        }

        const val SCOPE = "identify email guilds"
    }
}
