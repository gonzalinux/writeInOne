package com.gonzalinux.common

import org.springframework.web.reactive.function.server.ServerRequest
import reactor.util.context.Context
import reactor.util.context.ContextView
import java.util.UUID

data class RequestContext(
    val userId: Long,
    val requestId: String
)

object RequestContextHolder {
    const val CONTEXT_KEY = "REQUEST_CONTEXT"
    private const val REQUEST_HEADER = "gon-request-id"
    private const val REQUEST_FORMAT = "^rq-[0-9a-fA-F]{32}$"

    fun extractRequestId(request: ServerRequest): String {
        val requestHeader = request.headers().firstHeader(REQUEST_HEADER)
        if (requestHeader.isNullOrEmpty()) {
            return generateRequestId()
        }
        if (requestHeader.matches(REQUEST_FORMAT.toRegex())) {
            return requestHeader
        }
        return generateRequestId()
    }

    fun generateRequestId(): String {
        val randomPart = UUID.randomUUID().toString().replace("-", "")
        return "rq-$randomPart"
    }

    fun Context.withRequestContext(requestContext: RequestContext): Context {
        return put(CONTEXT_KEY, requestContext)
    }

    fun ContextView.getRequestContext(): RequestContext? {
        return getOrDefault(CONTEXT_KEY, null)
    }

    fun ContextView.hasRequestContext(): Boolean {
        return hasKey(CONTEXT_KEY)
    }
}

