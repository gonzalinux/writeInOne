package com.gonzalinux.config

import com.gonzalinux.common.RequestContextHolder.withUserId
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.domain.user.TokenService
import com.gonzalinux.domain.user.UserRepository
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

private val logger = KotlinLogging.logger {}
private const val BEARER_PREFIX = "Bearer "

/**
 * Sits in front of [JwtAuthFilter] on the REST API routes: an `Authorization: Bearer` token
 * authenticates a service account (MCP server, automation), and anything else falls through to
 * the normal cookie-based JWT flow — so a human session and a service account token work on the
 * exact same endpoints. See Collaboration.md's "Auth flow" section.
 */
@Component
class ServiceAccountAuthFilter(
    private val userRepository: UserRepository,
    private val tokenService: TokenService,
    private val jwtAuthFilter: JwtAuthFilter
) : HandlerFilterFunction<ServerResponse, ServerResponse> {

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        val token = request.headers().firstHeader("Authorization")
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.trim()
            ?: return jwtAuthFilter.filter(request, next)

        val tokenHash = tokenService.hashToken(token)
        return userRepository.findByServiceAccountTokenHash(tokenHash)
            .switchIfEmpty { Mono.error(UnauthorizedException()) }
            .flatMap { user ->
                logger.debug { "${request.method()} ${request.path()} [serviceAccountId=${user.id}]" }
                next.handle(request).contextWrite { it.withUserId(user.id) }
            }
    }
}
