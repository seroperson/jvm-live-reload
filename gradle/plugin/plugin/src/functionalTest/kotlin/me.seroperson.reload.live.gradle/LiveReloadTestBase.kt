package me.seroperson.reload.live.gradle

import okhttp3.OkHttpClient
import okhttp3.Request

abstract class LiveReloadTestBase {
    private val client = OkHttpClient()

    fun runUntil(
        url: String,
        expectedStatus: Int,
        expectedBody: String,
    ): Boolean {
        val request: Request = Request.Builder().url(url).build()

        try {
            val (code, body) =
                (
                    client.newCall(request).execute().use { response ->
                        response.code to response.body.string()
                    }
                )
            println("Requesting $url, got $code and $body")
            if (expectedStatus == code && expectedBody == body) {
                return true
            } else {
                Thread.sleep(500)
                return runUntil(url, expectedStatus, expectedBody)
            }
        } catch (ex: Exception) {
            println("Got exception: ${ex.message}")
            Thread.sleep(500)
            return runUntil(url, expectedStatus, expectedBody)
        }
    }
}
