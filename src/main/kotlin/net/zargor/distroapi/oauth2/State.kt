package net.zargor.distroapi.oauth2

import com.github.scribejava.core.oauth.OAuth20Service
import java.util.*

data class State(val state: String, val expiresAt: Date, val service: OAuth20Service)