package net.zargor.distroapi.extension

import java.util.*

fun Calendar.addMin(min: Int): Calendar {
    this.add(Calendar.MINUTE, min)
    return this
}

fun Calendar.addDay(day: Int): Calendar {
    this.add(Calendar.DATE, day)
    return this
}

fun Calendar.addSec(sec: Int): Calendar {
    this.add(Calendar.SECOND, sec)
    return this
}