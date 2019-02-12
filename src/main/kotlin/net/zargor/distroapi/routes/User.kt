package net.zargor.distroapi.routes

import io.javalin.Context
import net.zargor.distroapi.Authentication
import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.GSON
import net.zargor.distroapi.extension.resultJson


sealed class User {
    companion object {
        val atMe: Context.() -> Unit = {
            Authentication.needsToBeAuthenticated(this) { user ->
                this.resultJson(
                    data =
                    """{"id": "${user.id}", "username": {"name": "${user.username.name}", "discriminator": "${user.username.discriminator}"}, "discordId": "${user.discordClientId}", "email": "${user.email}", "avatar": "${user.getAvatarUrl()}"}"""
                )
            }
        }
        val atMeGuilds: Context.() -> Unit = {
            Authentication.needsToBeAuthenticated(this) { user ->
                val owner = DistroApi.instance.database.guildStorage.getGuildsByOwnerId(user.id)
                val moderator = DistroApi.instance.database.guildStorage.getGuildsByModerator(user.id)

                val ownerJson = GSON.toJson(owner)
                val moderatorJson = GSON.toJson(moderator)

                this.resultJson(data = """{"ownerOf": $ownerJson, "moderatorOf": $moderatorJson}""")
            }
        }
    }
}
