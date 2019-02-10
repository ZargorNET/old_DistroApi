package net.zargor.distroapi.extension

import net.zargor.distroapi.DistroApi
import net.zargor.distroapi.JwtToken
import spark.kotlin.RouteHandler

fun needsToBeAuthenticated(
    routeHandler: RouteHandler,
    function: spark.kotlin.RouteHandler.(token: JwtToken) -> Any
): Any {

    val authRegex = Regex("""^(Bearer )(\S+\.\S+\.\S+)$""")
    val authHeader = routeHandler.request.headers("Authorization") ?: return routeHandler.response.returnUnauthorized()
    val matchResult = authRegex.matchEntire(authHeader) ?: return routeHandler.response.returnUnauthorized()

    val jwt = matchResult.groupValues[2]
    val verifyRes = DistroApi.instance.jwt.verifyToken(jwt)
    if (!verifyRes.first || verifyRes.second == null)
        return routeHandler.response.returnUnauthorized()
    val token = verifyRes.second!!

    val user = DistroApi.instance.database.userStorage.getUserById(token.subject)
    if (user == null) {
        DistroApi.instance.logger.warn("User entered a valid JWT key but no user has been found in the database!")
        return routeHandler.response.returnUnauthorized()
    }
    if (!user.jwtTokenId.equals(token.jti, false))
        return routeHandler.response.returnUnauthorized()

    return function(routeHandler, token)
}