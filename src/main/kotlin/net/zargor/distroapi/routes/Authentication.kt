package net.zargor.distroapi.routes

import com.google.gson.JsonParser
import io.javalin.Context
import net.zargor.distro.databasemodels.model.Guild
import net.zargor.distro.databasemodels.model.User
import net.zargor.distro.databasemodels.model.Username
import net.zargor.distroapi.DISCORD_API_URL
import net.zargor.distroapi.DISCORD_OAUTH_SCOPE
import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.extension.addDay
import net.zargor.distroapi.extension.resultJson
import net.zargor.distroapi.util.Authentication
import net.zargor.distroapi.util.oauth2.OAuth2Exception
import net.zargor.distroapi.util.oauth2.OAuth2Token
import okhttp3.Request
import java.util.*

class Authentication {
    private data class UserGuild(val id: String, val iconHash: String, val name: String)

    suspend fun discord(ctx: Context) {
        val code = ctx.queryParam("code")
        if (code.isNullOrBlank()) {
            ctx.resultJson(error = "invalid_request").status(400)
            return
        }

        val token: OAuth2Token
        try {
            token = DistroApi.instance.discordOAuth.getToken(code)
        } catch (e: OAuth2Exception) {
            e.printStackTrace()
            ctx.resultJson(error = "code_invalid").status(400)
            return
        }
        if (token.scope != DISCORD_OAUTH_SCOPE) {
            ctx.resultJson(error = "scope_mismatch").status(400)
            return
        }

        //GET USER INFO
        val discordUserReq = Request.Builder().url("$DISCORD_API_URL/users/@me").get()
        token.signRequest(discordUserReq)
        val discordUserRes = DistroApi.instance.discordHttpClient.doReq(discordUserReq.build(), true).await()
        var user: User? = null


        val jsonParser = JsonParser()
        discordUserRes.use { res ->
            if (!res.isSuccessful || res.body() == null) {
                ctx.resultJson(error = "could_not_fetch_discord_user").status(400)
                return
            }

            val discordRes = jsonParser.parse(res.body()!!.charStream()).asJsonObject
            val discordUserId = discordRes.get("id").asString
            val discordUserEmail = discordRes.get("email").asString
            val discordUserUsername = discordRes.get("username").asString
            val discordUserDiscriminator = discordRes.get("discriminator").asString
            val discordUserAvatarHash = discordRes.get("avatar").asString

            user = DistroApi.instance.database.userStorage.getUserByDiscordId(discordUserId)
                ?: User(UUID.randomUUID().toString(), discordUserId)
            user!!.email = discordUserEmail
            user!!.username = Username(discordUserUsername, discordUserDiscriminator)
            user!!.avatarHash = discordUserAvatarHash
        }

        if (user == null)
            throw IllegalStateException("User is null")


        //GET GUILDS
        val discordGuildReq = Request.Builder().url("$DISCORD_API_URL/users/@me/guilds").get()
        token.signRequest(discordGuildReq)
        val discordGuildRes = DistroApi.instance.discordHttpClient.doReq(discordGuildReq.build()).await()

        val guilds: MutableList<UserGuild> = mutableListOf()

        discordGuildRes.use { res ->
            if (!res.isSuccessful || res.body() == null) {
                ctx.resultJson(error = "could_not_fetch_discord_user_guilds").status(400)
                return
            }

            val guildDiscordRes = jsonParser.parse(res.body()!!.charStream()).asJsonArray
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
        }

        val dbGuilds = DistroApi.instance.database.guildStorage.getGuildsByOwnerId(user!!.id).toMutableList()
        guilds.forEach {
            if (dbGuilds.none { g -> g.discordId == it.id })
                dbGuilds.add(
                    Guild(
                        UUID.randomUUID().toString(),
                        it.id,
                        user!!.id,
                        mutableListOf(),
                        it.name,
                        it.iconHash
                    )
                )
            else
                dbGuilds.removeIf { g -> g.discordId == it.id }
        }

        dbGuilds.forEach {
            DistroApi.instance.database.guildStorage.save(it)
        }


        //GENERATE AUTHENTICATION JWT
        val tokenPair = DistroApi.instance.jwt.createToken(user!!.id, Calendar.getInstance().addDay(14).time)

        user!!.jwtTokenId = tokenPair.second.jti

        DistroApi.instance.database.userStorage.save(user!!)

        ctx.resultJson(data = """{"jwt": {"key": "${tokenPair.first}", "expiresAt": ${tokenPair.second.expiresAt.time}}}""")
    }

    fun discordInfo(ctx: Context) {
        ctx.resultJson(data = """{"id": "${DistroApi.instance.config.oAuth.discord.clientId}", "scopes": "$DISCORD_OAUTH_SCOPE"}""")
    }

    fun revoke(ctx: Context) {
        Authentication.needsToBeAuthenticated(ctx) { user ->
            user.jwtTokenId = "-revoked"
            DistroApi.instance.database.userStorage.save(user)
            ctx.resultJson(data = """{"msg": "ok"}""")
        }
    }
}
