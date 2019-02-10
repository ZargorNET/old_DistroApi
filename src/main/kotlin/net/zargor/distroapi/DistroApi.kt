package net.zargor.distroapi

import com.google.gson.Gson
import com.mongodb.MongoCredential
import net.zargor.distro.databasemodels.Database
import net.zargor.distroapi.extension.json
import net.zargor.distroapi.extension.return404
import net.zargor.distroapi.routes.Authentication
import net.zargor.distroapi.routes.User
import org.slf4j.LoggerFactory
import spark.Spark
import spark.Spark.*
import spark.kotlin.DEFAULT_ACCEPT
import spark.kotlin.get
import spark.kotlin.options
import java.io.File
import kotlin.system.exitProcess

private const val API_VERSION: Byte = 1
private const val CONFIG_PATH = "config.toml"
const val DISCORD_API_URL = "https://discordapp.com/api/v6"
val GSON = Gson()

fun main(args: Array<String>) {
    DistroApi()
}

class DistroApi {
    val logger = LoggerFactory.getLogger("main")
    val config: Config
    val database: Database
    val jwt: Jwt

    init {
        instance = this
        val _cfgFile = File(CONFIG_PATH)
        val _cfg = Config.load(_cfgFile)
        if (_cfg == null) {
            Config(
                Config.WebServer("127.0.0.1", 8080, "http://localhost:8080", "http://localhost:8080"),
                Config.MongoDb("127.0.0.1", 27017, "distro", "distro", "your_pw", "distro"),
                Config.Authenticator("your-secure-jwt-key"),
                Config.OAuth2(
                    Config.OAuth2.Discord("your_discord_client_id", "your_discord_secret")
                )
            )
                .save(_cfgFile)
            logger.warn("Config file created! Go check it out")
            exitProcess(-1)
        }
        this.config = _cfg

        this.jwt = Jwt(this.config.authenticator.privateJwtKey)

        this.database = Database(
            this.config.mongoDb.host,
            this.config.mongoDb.port,
            this.config.mongoDb.db,
            MongoCredential.createCredential(
                this.config.mongoDb.authUsername,
                this.config.mongoDb.authDb,
                this.config.mongoDb.authPassword.toCharArray()
            )
        )


        Spark.ipAddress(this.config.webserver.bindHost)
        Spark.port(this.config.webserver.port)

        notFound { _, response ->
            response.return404()
        }
        internalServerError { _, response ->
            response.json(error = "internal_server_error", responseCode = 500)
        }
        options("*") {
            response.status(200)
        }

        get("/") {
            response.json(data = """{"version": $API_VERSION}""")
        }
        path("/authentication") {
            get("/discord", DEFAULT_ACCEPT, Authentication.discord)
            get("/discord/info", DEFAULT_ACCEPT, Authentication.discordInfo)
            get("/revoke", DEFAULT_ACCEPT, Authentication.revoke)
        }
        path("/user") {
            get("/@me", DEFAULT_ACCEPT, User.atMe)
        }

        spark.kotlin.after {
            response.header("Access-Control-Allow-Origin", this@DistroApi.config.webserver.allowCORS)
            response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            response.header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization")
        }
        init()
    }

    companion object {
        lateinit var instance: DistroApi
            private set
    }
}

