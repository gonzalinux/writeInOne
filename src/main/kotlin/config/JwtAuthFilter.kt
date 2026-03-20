package com.gonzalinux.config

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.common.RequestContext
import com.gonzalinux.common.RequestContextHolder
import com.gonzalinux.common.RequestContextHolder.withRequestContext
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.domain.user.TokenService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

private val logger = KotlinLogging.logger {}

@Component
class JwtAuthFilter(private val tokenService: TokenService) : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val token = request.cookies()[ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: throw UnauthorizedException()

        return Mono.fromCallable { tokenService.getUserIdFromToken(token) }
            .map {
                RequestContext(it, RequestContextHolder.extractRequestId(request))
            }
            .onErrorMap {
                logger.info { "Error $it"}
                throw UnauthorizedException() }
            .flatMap { reqContext->
                logger.debug { "${request.method()} ${request.path()} [requestId=${reqContext.requestId}, userId=${reqContext.userId}]" }
                next.handle(request).contextWrite { it.withRequestContext(reqContext) }
            }


    }
}
