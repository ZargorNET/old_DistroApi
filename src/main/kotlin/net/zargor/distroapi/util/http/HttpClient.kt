package net.zargor.distroapi.util.http

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Http client which consider rate limits
 */
abstract class HttpClient {
    private data class HttpRequest(
        val req: Request,
        val deferred: CompletableDeferred<Response>,
        val ignoreRateLimit: Boolean
    )

    var debug: Boolean = false

    private val logger = LoggerFactory.getLogger(this::class.java.name)

    private val internalHttpClient = OkHttpClient()
    private lateinit var requestsChannel: Channel<HttpRequest>
    private var running = false
    private val waitTime = AtomicLong(0)

    private val remainingHeaderName: String
    private val resetHeaderName: String

    init {
        this.remainingHeaderName = this.remainingHeader()
        this.resetHeaderName = this.resetHeader()
    }

    suspend fun doReq(req: Request, ingoreRateLimit: Boolean = false): CompletableDeferred<Response> {
        if (!this.running)
            throw IllegalStateException("Can't make any requests before calling start() method")

        val deferred = CompletableDeferred<Response>()

        this.requestsChannel.send(HttpRequest(req, deferred, ingoreRateLimit))

        return deferred
    }


    fun start() = GlobalScope.launch {
        if (this@HttpClient.running)
            return@launch
        this@HttpClient.requestsChannel = Channel(Channel.UNLIMITED)
        this@HttpClient.running = true

        while (this@HttpClient.running) {
            val req = this@HttpClient.requestsChannel.receive()

            this@HttpClient.internalHttpClient.newCall(req.req).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (this@HttpClient.debug) {
                        this@HttpClient.logger.info(
                            """
                        ----------------------------------
                        Made request to: ${response.request().url()}
                        Request-Header: ${response.request().headers()}
                        Response is: $response
                        Response-Header: ${response.headers()}
                        ----------------------------------
                    """.trimIndent()
                        )
                    }

                    if (!req.ignoreRateLimit) {
                        val remaining = response.header(this@HttpClient.remainingHeaderName)
                        val reset = response.header(this@HttpClient.resetHeaderName)
                        if (remaining != null && reset != null) {
                            val remainingInt = remaining.toInt()
                            val resetLong = reset.toLong()

                            if (remainingInt == 0)
                                this@HttpClient.waitTime.set(resetLong - Date().time)
                        } else {
                            this@HttpClient.logger.warn("Server responded with missing remaining/reset header on url: ${req.req.url()}")
                        }
                    }
                    req.deferred.complete(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    req.deferred.completeExceptionally(e)
                }
            })

            val wait = this@HttpClient.waitTime.get()
            if (wait > 0)
                delay(wait)
        }
    }

    fun stop() {
        this.running = false
        this.requestsChannel.cancel()
    }

    protected abstract fun remainingHeader(): String
    protected abstract fun resetHeader(): String
}