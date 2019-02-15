package net.zargor.distroapi.util

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.zargor.distroapi.extension.addSec
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class RateLimiter {
    private var running = false
    private val map = mutableMapOf<String, Date>() //JWT TOKEN, TIME WHEN TO REMOVE
    private val lock = ReentrantReadWriteLock()


    fun block(token: String, expireTime: Date = Calendar.getInstance().addSec(1).time): Date {
        if (!this.running)
            throw IllegalStateException("Cannot count before start() method is called")

        this.lock.write {
            this.map[token] = expireTime
        }

        return expireTime
    }

    fun isBlocked(token: String): Boolean {
        this.lock.read {
            return this.map[token] != null
        }
    }

    fun start() = GlobalScope.launch {
        if (this@RateLimiter.running)
            return@launch
        this@RateLimiter.running = true

        while (this@RateLimiter.running) {
            val now = Date()
            val toDelete = mutableListOf<String>()
            this@RateLimiter.lock.read {
                this@RateLimiter.map.forEach { k, v ->
                    if (now.after(v))
                        toDelete.add(k)
                }
            }
            this@RateLimiter.lock.write {
                toDelete.forEach {
                    this@RateLimiter.map.remove(it)
                }

            }
            delay(1000)
        }
    }

    fun stop() {
        if (!this.running)
            return
        this.running = false
        this.map.clear()
    }
}