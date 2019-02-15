package net.zargor.distroapi.util.oauth2

import com.google.gson.JsonParser
import net.zargor.distroapi.util.http.HttpClient
import okhttp3.FormBody
import okhttp3.Request
import java.io.Reader
import java.net.URLEncoder

abstract class OAuth2Service(
    private val clientId: String,
    private val clientSecret: String,
    private val scope: String,
    private val redirectUri: String,
    private val httpClient: HttpClient,
    private val httpAgent: String = "Mozilla/5.0"
) {

    suspend fun getToken(code: String): OAuth2Token {
        val requestBody = FormBody.Builder()
            .add("client_id", this.clientId)
            .add("client_secret", this.clientSecret)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", this.redirectUri)
            .add("scope", this.scope)
            .build()
        val req = Request.Builder()
            .url(this.tokenUrl())
            .post(requestBody)
            .addHeader("User-Agent", this.httpAgent)
            .build()

        val res = this.httpClient.doReq(req, true).await()
        res.use {
            if (!res.isSuccessful)
                throw OAuth2Exception(
                    "Server has not returned with an OK-status. Response code is: ${res.code()} Body: ${res.body()?.string()
                        ?: "null"}"
                )
            val body = res.body() ?: throw OAuth2Exception("Response body is null")

            return parseAccessTokenResonse(body.charStream())
        }
    }

    suspend fun refreshToken(token: OAuth2Token): OAuth2Token {
        val requestBody = FormBody.Builder()
            .add("client_id", this.clientId)
            .add("client_secret", this.clientSecret)
            .add("grant_type", "refresh_token")
            .add("refresh_token", token.refreshToken)
            .add("redirect_uri", this.redirectUri)
            .add("scope", this.scope)
            .build()
        val req = Request.Builder()
            .url(this.tokenUrl())
            .post(requestBody)
            .addHeader("User-Agent", this.httpAgent)
            .build()

        val res = this.httpClient.doReq(req, true).await()
        res.use {
            if (!res.isSuccessful)
                throw OAuth2Exception("Server has not returned with an OK-status. Response code is: ${res.code()}")
            val body = res.body() ?: throw OAuth2Exception("Response body is null")

            return this.parseAccessTokenResonse(body.charStream())
        }
    }

    suspend fun revokeToken(token: OAuth2Token) {
        val requestBody = FormBody.Builder()
            .add("token", token.accessToken)
            .build()
        val req = Request.Builder()
            .url(this.revokeTokenUrl())
            .post(requestBody)
            .addHeader("User-Agent", this.httpAgent)
            .build()

        val res = this.httpClient.doReq(req, true).await()
        res.use {
            if (!res.isSuccessful)
                throw OAuth2Exception("Server has not returned with an OK-status. Response code is: ${res.code()}")
        }
    }

    fun getAuthroizeUrl(state: String? = null): String {
        var s = """${this.authorizeUrl()}?response_type=code&client_id=${this.clientId}&scope=${URLEncoder.encode(
            this.scope,
            "UTF-8"
        )}&redirect_uri=${URLEncoder.encode(this.redirectUri, "UTF-8")}"""
        if (state != null)
            s += "&state=$state"
        return s
    }

    protected abstract fun authorizeUrl(): String
    protected abstract fun tokenUrl(): String
    protected abstract fun revokeTokenUrl(): String

    private fun parseAccessTokenResonse(body: Reader): OAuth2Token {
        val jsonParser = JsonParser()

        try {
            val jsonMain = jsonParser.parse(body).asJsonObject
            val jsonAccessToken = jsonMain.get("access_token").asString
            val jsonTokenType = jsonMain.get("token_type").asString
            val jsonExpiresIn = jsonMain.get("expires_in").asLong
            val jsonRefreshToken = jsonMain.get("refresh_token").asString
            val jsonScope = jsonMain.get("scope").asString

            return OAuth2Token(jsonAccessToken, jsonTokenType, jsonExpiresIn, jsonRefreshToken, jsonScope)
        } catch (e: IllegalStateException) {
            throw OAuth2Exception("Could not read json data", e)
        } catch (e: NullPointerException) {
            throw OAuth2Exception("Missing json field in response", e)
        }
    }
}
