package com.gonzalinux.config

import com.gonzalinux.common.RequestContextHolder
import com.gonzalinux.common.RequestContextHolder.withRequestId
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Int.MIN_VALUE)
class RequestIdFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = RequestContextHolder.extractRequestId(exchange.request)
        return chain.filter(exchange).contextWrite { it.withRequestId(requestId) }
    }
}
