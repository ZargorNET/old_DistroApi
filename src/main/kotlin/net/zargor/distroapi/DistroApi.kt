package net.zargor.distroapi

import com.google.gson.Gson
import com.mongodb.MongoCredential
import io.javalin.Context
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.zargor.distro.databasemodels.Database
import net.zargor.distroapi.extension.resultJson
import net.zargor.distroapi.routes.Authentication
import net.zargor.distroapi.routes.User
import org.eclipse.jetty.server.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
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
                ), false
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
            app.options("*") { ctx ->
                ctx.result(this.runAsync(ctx) {
                    this.status(200)
                })
            }

            get("/") { ctx ->
                ctx.result(this.runAsync(ctx) {
                    this.resultJson(data = """{"version": $API_VERSION}""")
                })
            }

            path("/user") {
                get("/@me") { ctx -> ctx.result(this.runAsync(ctx, User.atMe)) }
                get("/@me/guilds") { ctx -> ctx.result(this.runAsync(ctx, User.atMeGuilds)) }
            }

            path("/authentication") {
                get("/discord") { ctx -> ctx.result(this.runAsync(ctx, Authentication.discord)) }
                get("/discord/info") { ctx -> ctx.result(this.runAsync(ctx, Authentication.discordInfo)) }
                get("/revoke") { ctx -> ctx.result(this.runAsync(ctx, Authentication.revoke)) }
            }
        }


        app.after { ctx ->
            ctx.header("Server", "Distro")
            ctx.header("Access-Control-Allow-Origin", this@DistroApi.config.webserver.allowCORS)
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            ctx.header("Access-Control-Allow-Headers", "Origin, Content-Type, Authorization")
        }

        app.start()
    }

    private fun runAsync(context: Context, path: Context.() -> Unit): CompletableFuture<Context> {
        val future = CompletableFuture<Context>()
        GlobalScope.launch {
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

