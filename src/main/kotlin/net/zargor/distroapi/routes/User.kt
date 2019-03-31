package net.zargor.distroapi.routes

import io.javalin.Context
import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.GSON
import net.zargor.distroapi.extension.resultJson
import net.zargor.distroapi.util.Authentication
import org.eclipse.jetty.http.HttpStatus
import java.util.*


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

        val id: Context.() -> Unit = {
            Authentication.needsToBeAuthenticated(this) { _ ->
                val id = this.pathParam("id")
                val uuid: UUID
                try {
                    uuid = UUID.fromString(id)
                } catch (e: IllegalArgumentException) {
                    this.resultJson(error = "invalid_id").status(HttpStatus.BAD_REQUEST_400)
                    return@needsToBeAuthenticated
                }

                val user = DistroApi.instance.database.userStorage.getUserById(uuid)
                if (user == null) {
                    this.resultJson(error = "user_not_found").status(HttpStatus.NOT_FOUND_404)
                    return@needsToBeAuthenticated
                }

                this.resultJson(data = GSON.toJson(user.toJsonPublic()))
            }
        }
    }
}
