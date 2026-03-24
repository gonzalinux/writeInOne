package com.gonzalinux.config

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class AdminHostFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val domain = (request.headers().firstHeader("X-Site-Host")
            ?: request.headers().firstHeader("Host"))
            ?.substringBefore(":")
            ?: return ServerResponse.notFound().build()

        return if (domain == "writeinone.com" || domain == "localhost") {
            next.handle(request)
        } else {
            ServerResponse.notFound().build()
        }
    }
}
