package net.zargor.distroapi.util.http.impl

import net.zargor.distroapi.util.http.HttpClient

class DiscordHttpClient : HttpClient() {

    override fun remainingHeader(): String {
        return "X-RateLimit-Remaining"
    }

    override fun resetHeader(): String {
        return "X-RateLimit-Reset"
    }

}