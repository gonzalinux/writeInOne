package com.gonzalinux.common

import org.springframework.http.server.reactive.ServerHttpRequest
import reactor.util.context.Context
import reactor.util.context.ContextView
import java.util.*

object RequestContextHolder {
    const val REQUEST_ID_KEY = "REQUEST_ID"
    const val USER_ID_KEY = "USER_ID"
    private const val REQUEST_HEADER = "x-request-id"
    private const val REQUEST_FORMAT = "^GK[0-9a-fA-F]{18}$"

    fun extractRequestId(request: ServerHttpRequest): String {
        val header = request.headers.getFirst(REQUEST_HEADER)
        if (!header.isNullOrEmpty() && header.matches(REQUEST_FORMAT.toRegex())) {
            return header
        }
        return generateRequestId()
    }

    fun generateRequestId(): String {
        val randomPart = UUID.randomUUID().toString().replace("-", "").substring(0,18)
        return "GK$randomPart"
    }

    fun Context.withRequestId(requestId: String): Context = put(REQUEST_ID_KEY, requestId)
    fun Context.withUserId(userId: Long): Context = put(USER_ID_KEY, userId)

    fun ContextView.getRequestId(): String? = getOrDefault(REQUEST_ID_KEY, null)
    fun ContextView.getUserId(): Long? = getOrDefault(USER_ID_KEY, null)
}
