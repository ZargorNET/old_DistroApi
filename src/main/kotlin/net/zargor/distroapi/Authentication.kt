package net.zargor.distroapi

import io.javalin.Context
import net.zargor.distro.databasemodels.model.User
import net.zargor.distroapi.extension.resultJson
import org.eclipse.jetty.http.HttpStatus

sealed class Authentication {
    companion object {
        fun needsToBeAuthenticated(ctx: Context, func: (user: User) -> Unit) {
            val user = handle(ctx)
            if (user == null)
                ctx.resultJson(error = "unauthorized").status(HttpStatus.UNAUTHORIZED_401)
            else
                func(user)
        }

        private fun handle(ctx: Context): User? {
            val authRegex = Regex("""^(Bearer )(\S+\.\S+\.\S+)$""")
            val authHeader = ctx.req.getHeader("Authorization") ?: return null
            val matchResult = authRegex.matchEntire(authHeader) ?: return null

            val jwt = matchResult.groupValues[2]
            val verifyRes = DistroApi.instance.jwt.verifyToken(jwt)
            if (!verifyRes.first || verifyRes.second == null)
                return null
            val token = verifyRes.second!!

            val user = DistroApi.instance.database.userStorage.getUserById(token.subject)
            if (user == null) {
                DistroApi.instance.logger.warn("User entered a valid JWT key but no user has been found in the database!")
                return null
            }
            if (!user.jwtTokenId.equals(token.jti, false))
                return null

            return user
        }
    }
}