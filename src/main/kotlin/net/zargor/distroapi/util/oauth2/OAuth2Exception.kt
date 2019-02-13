package net.zargor.distroapi.util.oauth2

class OAuth2Exception(msg: String, cause: Throwable?) : RuntimeException(msg, cause) {
    constructor(msg: String) : this(msg, null)
}