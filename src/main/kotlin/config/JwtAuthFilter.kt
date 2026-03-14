package com.gonzalinux.config

import com.gonzalinux.domain.user.TokenService
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class JwtAuthFilter(private val tokenService: TokenService) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val token = exchange.request.cookies["access_token"]?.firstOrNull()?.value ?: return chain.filter(exchange)

        return try {
            val userId = tokenService.getUserIdFromToken(token)
            val auth = UsernamePasswordAuthenticationToken(
                userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
            )
            chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
        } catch (e: Exception) {
            chain.filter(exchange)
        }
    }
}
