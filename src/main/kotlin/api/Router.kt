package com.gonzalinux.api

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse

@Configuration
class Router(private val authHandler: AuthHandler) {

    @Bean
    fun routes(): RouterFunction<ServerResponse> = route()
        .POST("/auth/register", authHandler::register)

        .build()

        // will filter paths that need authentication
    private fun authenticated(handler: HandlerFunction<ServerResponse>): HandlerFunction<ServerResponse> =
        HandlerFunction { request ->
            request.principal()
                .flatMap { handler.handle(request) }
                .switchIfEmpty(ServerResponse.status(HttpStatus.UNAUTHORIZED).build())
        }
}
