package net.zargor.distroapi.util

import io.javalin.Context
import net.zargor.distro.databasemodels.model.User
import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.JwtToken
import net.zargor.distroapi.extension.resultJson
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory

sealed class Authentication {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.name)

        fun needsToBeAuthenticated(ctx: Context, rateLimit: Boolean = true, func: (user: User) -> Unit) {
            val token = this.getToken(ctx)
            if (token == null) {
                ctx.resultJson(error = "unauthorized").status(HttpStatus.UNAUTHORIZED_401)
                return
            }
            if (rateLimit) {
                if (DistroApi.instance.rateLimiter.isBlocked(token.subject)) {
                    ctx.resultJson(error = "rate_limit_exceeded").status(HttpStatus.TOO_MANY_REQUESTS_429)
                    return
                }
                val expire = DistroApi.instance.rateLimiter.block(token.subject)
                ctx.header("X-RateLimit-Remaining", "0")
                ctx.header("X-RateLimit-Reset", expire.time.toString())
            }
            val user = this.getUser(token)
            if (user == null) {
                ctx.resultJson(error = "unauthorized").status(HttpStatus.UNAUTHORIZED_401)
                return
            }

            func(user)

        }

        private fun getUser(token: JwtToken): User? {
            val user = DistroApi.instance.database.userStorage.getUserById(token.subject) ?: return null

            if (!user.jwtTokenId.equals(token.jti, false))
                return null

            return user
        }

        private fun getToken(ctx: Context): JwtToken? {
            val authRegex = Regex("""^(Bearer )(\S+\.\S+\.\S+)$""")
            val authHeader = ctx.req.getHeader("Authorization") ?: return null
            val matchResult = authRegex.matchEntire(authHeader) ?: return null

            val jwt = matchResult.groupValues[2]
            val verifyRes = DistroApi.instance.jwt.verifyToken(jwt)
            if (!verifyRes.first || verifyRes.second == null)
                return null
            return verifyRes.second
        }
    }
}