package net.zargor.distroapi.extension

import spark.Response


fun Response.json(data: String = "", error: String = "null", responseCode: Int = 200): String {
    var data = data
    if (data.isBlank())
        data = "{}"
    this.body("""{"error":"$error", "data": $data}""")
    this.status(responseCode)
    this.type("application/json")
    return this.body()
}

fun Response.returnUnauthorized(): String {
    return this.json(error = "unauthorized", responseCode = 401)
}

fun Response.return404(): String {
    return this.json(error = "not_found", responseCode = 404)
}