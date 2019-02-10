package net.zargor.distroapi

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import net.zargor.distroapi.extension.addMin
import org.apache.commons.lang3.RandomStringUtils
import java.util.*

class Jwt(private val jwtSecret: String) {
    private val algorithm = Algorithm.HMAC512(this.jwtSecret)
    private val verifier = JWT.require(this.algorithm).withIssuer("distro").build()

    fun createToken(userId: String, expiresAt: Date = Calendar.getInstance().addMin(30).time): Pair<String, JwtToken> {
        val jwtToken = JwtToken(
            "distro",
            userId,
            expiresAt,
            RandomStringUtils.randomAlphanumeric(16) //FINE BECAUSE EACH JTI IS LOCKED DOWN TO AN USER ITSELF
        )
        val token = JWT.create()
            .withIssuer(jwtToken.issuer)
            .withSubject(jwtToken.subject)
            .withExpiresAt(jwtToken.expiresAt)
            .withJWTId(jwtToken.jti)
            .sign(this.algorithm)

        return Pair(token, jwtToken)
    }

    /**
     * Verifies a token and check if the token is validly signed
     * @param token The token to verify
     * @return ``false, null`` if invalid, ``true, *something*`` otherwise
     */
    fun verifyToken(token: String): Pair<Boolean, JwtToken?> {
        return try {
            val decoded = this.verifier.verify(token)
            val token = JwtToken(decoded.issuer, decoded.subject, decoded.expiresAt, decoded.id)
            Pair(true, token)
        } catch (e: JWTVerificationException) {
            e.printStackTrace()
            Pair(false, null)
        }
    }
}

data class JwtToken(val issuer: String, val subject: String, val expiresAt: Date, val jti: String)