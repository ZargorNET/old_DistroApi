package net.zargor.distroapi

import com.google.gson.Gson
import com.mongodb.MongoCredential
import io.javalin.Context
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.zargor.distro.databasemodels.Database
import net.zargor.distroapi.extension.resultJson
import net.zargor.distroapi.routes.Authentication
import net.zargor.distroapi.routes.User
import net.zargor.distroapi.util.RateLimiter
import net.zargor.distroapi.util.http.impl.DiscordHttpClient
import net.zargor.distroapi.util.oauth2.OAuth2Service
import net.zargor.distroapi.util.oauth2.impl.DiscordOAuth2Service
import org.eclipse.jetty.server.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

private const val API_VERSION: Byte = 1
private const val CONFIG_PATH = "config.toml"
const val DISCORD_API_URL = "https://discordapp.com/api/v6"
const val DISCORD_OAUTH_SCOPE = "identify email guilds"

val GSON = Gson()

fun main(args: Array<String>) {
    DistroApi()
}

class DistroApi {
    val logger = LoggerFactory.getLogger("main")
    val config: Config
    val database: Database
    val jwt: Jwt
    val discordHttpClient = DiscordHttpClient()
    val discordOAuth: OAuth2Service
    val rateLimiter = RateLimiter()

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
                    Config.OAuth2.Discord(
                        "your_discord_client_id",
                        "your_discord_secret",
                        "http://localhost:8081/callback/discord"
                    )
                ), false
            )
                .save(_cfgFile)
            logger.warn("Config file created! Go check it out")
            exitProcess(-1)
        }
        this.config = _cfg

        this.jwt = Jwt(this.config.authenticator.privateJwtKey)
        this.discordOAuth = DiscordOAuth2Service(
            this.config.oAuth.discord.clientId,
            this.config.oAuth.discord.clientSecret,
            DISCORD_OAUTH_SCOPE,
            this.config.oAuth.discord.redirectUri,
            this.discordHttpClient
        )

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


        val app = Javalin.create().server {
            val server = Server(InetSocketAddress(this.config.webserver.bindHost, this.config.webserver.port))
            return@server server
        }
        if (this.config.debug)
            app.enableDebugLogging()

        app.error(404) { ctx ->
            ctx.resultJson(error = "not_found").status(404)
        }

        app.error(500) { ctx ->
            ctx.resultJson(error = "internal_server_error").status(500)
        }

        app.routes {
            get("/") { ctx ->
                ctx.result(this.runAsync(ctx) {
                    ctx.resultJson(data = """{"version": $API_VERSION}""")
                })
            }

            path("/user") {
                get("/@me") { ctx -> ctx.result(this.runAsync(ctx, User.atMe)) }
                get("/@me/guilds") { ctx -> ctx.result(this.runAsync(ctx, User.atMeGuilds)) }
                get("/:id") { ctx -> ctx.result(this.runAsync(ctx, User.id)) }
            }

            path("/authentication") {
                val authentication = Authentication()

                get("/discord") { ctx -> ctx.result(this.runSuspendAsync(ctx, authentication::discord)) }
                get("/discord/info") { ctx -> ctx.result(this.runAsync(ctx, authentication::discordInfo)) }
                get("/revoke") { ctx -> ctx.result(this.runAsync(ctx, authentication::revoke)) }
            }
        }


        app.after { ctx ->
            if (ctx.method() == "OPTIONS")
                ctx.status(200)
            ctx.header("Server", "Distro")
            ctx.header("Access-Control-Allow-Origin", this@DistroApi.config.webserver.allowCORS)
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            ctx.header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization")
        }

        this.discordHttpClient.debug = this.config.debug
        this.discordHttpClient.start()
        this.rateLimiter.start()
        app.start()
    }

    private fun runSuspendAsync(context: Context, path: suspend (ctx: Context) -> Unit): CompletableFuture<Context> {
        val future = CompletableFuture<Context>()
        GlobalScope.launch(Dispatchers.IO) {
            path(context)
            future.complete(context)
        }

        return future
    }

    private fun runAsync(context: Context, path: (Context) -> (Unit)): CompletableFuture<Context> {
        val future = CompletableFuture<Context>()
        GlobalScope.launch(Dispatchers.IO) {
            path(context)
            future.complete(context)
        }

        return future
    }


    companion object {
        lateinit var instance: DistroApi
            private set
    }
}


