package net.zargor.distroapi.routes

import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.extension.json
import net.zargor.distroapi.extension.needsToBeAuthenticated
import spark.kotlin.RouteHandler

sealed class User {
    companion object {
        val atMe: (RouteHandler).() -> Any = get@{
            needsToBeAuthenticated(this) ntba@{ token ->
                val user =
                    DistroApi.instance.database.userStorage.getUserById(token.subject) ?: return@ntba response.json(
                        error = "user_not_found",
                        responseCode = 400
                    )

                response.json(
                    data =
                    """{"id": "${user.id}", "username": {"name": "${user.username.name}", "discriminator": "${user.username.discriminator}"}, "discordId": "${user.discordClientId}", "email": "${user.email}", "avatar": "${user.getAvatarUrl()}"}"""
                )
            }
        }
    }
}