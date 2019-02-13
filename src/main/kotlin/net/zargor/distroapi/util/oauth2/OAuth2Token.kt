package net.zargor.distroapi.util.oauth2

import com.google.gson.annotations.SerializedName
import okhttp3.Request

data class OAuth2Token(
    @field:SerializedName("access_token")
    val accessToken: String,
    @field:SerializedName("token_type")
    val tokenType: String,
    @field:SerializedName("expires_in")
    val expiresIn: Long,
    @field:SerializedName("refresh_token")
    val refreshToken: String,
    @field:SerializedName("scope")
    val scope: String
) {
    fun signRequest(req: Request.Builder) {
        req.header("Authorization", "${this.tokenType} ${this.accessToken}")
    }
}