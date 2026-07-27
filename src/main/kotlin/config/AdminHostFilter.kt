package com.gonzalinux.config

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class AdminHostFilter(private val subdomainProperties: SubdomainProperties) :
    HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val domain = (request.headers().firstHeader("X-Site-Host")
            ?: request.headers().firstHeader("Host"))
            ?: return ServerResponse.notFound().build()

        return if (subdomainProperties.isHomeDomain(domain)) {
            next.handle(request)
        } else {
            ServerResponse.notFound().build()
        }
    }
}
