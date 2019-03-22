package net.zargor.distroapi.routes

import io.javalin.Context
import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.GSON
import net.zargor.distroapi.extension.resultJson
import net.zargor.distroapi.util.Authentication


sealed class User {
    companion object {
        val atMe: Context.() -> Unit = {
            Authentication.needsToBeAuthenticated(this) { user ->
                this.resultJson(data = user.toJson().toString())
            }
        }
        val atMeGuilds: Context.() -> Unit = {
            Authentication.needsToBeAuthenticated(this) { user ->
                val owner = DistroApi.instance.database.guildStorage.getGuildsByOwnerId(user.id)
                    .map { it.toJson() }

                val moderator =
                    DistroApi.instance.database.guildStorage.getGuildsByModerator(user.id).map { it.toJson() }

                val ownerJson = GSON.toJson(owner)
                val moderatorJson = GSON.toJson(moderator)

                this.resultJson(data = """{"ownerOf": $ownerJson, "moderatorOf": $moderatorJson}""")
            }
        }
    }
}
