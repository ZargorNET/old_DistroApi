package net.zargor.distroapi

import com.moandjiezana.toml.Toml
import com.moandjiezana.toml.TomlWriter
import java.io.File

data class Config(
    val webserver: WebServer,
    val mongoDb: MongoDb,
    val authenticator: Authenticator,
    val oAuth: OAuth2,
    val debug: Boolean
) {
    data class WebServer(val bindHost: String, val port: Int, val url: String, val allowCORS: String)
    data class MongoDb(
        val host: String,
        val port: Int,
        val authDb: String,
        val authUsername: String,
        val authPassword: String,
        val db: String
    )

    data class Authenticator(val privateJwtKey: String)
    data class OAuth2(val discord: Discord) {
        data class Discord(val clientId: String, val clientSecret: String, val redirectUri: String)
    }

    fun save(f: File) {
        if (!f.exists())
            f.createNewFile()
        TomlWriter().write(this, f)
    }

    companion object {
        fun load(f: File): Config? {
            if (!f.exists())
                return null
            return Toml().read(f).to(Config::class.java)
        }
    }
}