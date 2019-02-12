package net.zargor.distroapi.extension

import io.javalin.Context

fun Context.resultJson(data: String = "", error: String = "null"): Context {
    var data = data
    if (data.isBlank())
        data = "{}"
    this.contentType("application/json")
    this.result("""{"error":"$error", "data": $data}""")
    return this
}