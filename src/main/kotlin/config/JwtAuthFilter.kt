package com.gonzalinux.config

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.common.RequestContextHolder.withUserId
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.domain.user.TokenService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
class JwtAuthFilter(private val tokenService: TokenService) : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val token = request.cookies()[ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: throw UnauthorizedException()

        return Mono.fromCallable { tokenService.getUserIdFromToken(token) }
            .onErrorMap {
                logger.info { "Error $it" }
                throw UnauthorizedException()
            }
            .flatMap { userId ->
                logger.debug { "${request.method()} ${request.path()} [userId=$userId]" }
                next.handle(request).contextWrite { it.withUserId(userId) }
            }
    }
}
