package com.gonzalinux.api

import com.gonzalinux.api.data.RegisterRequest
import com.gonzalinux.api.data.AuthResponse
import com.gonzalinux.api.data.LoginRequest
import com.gonzalinux.common.RequestValidator
import com.gonzalinux.domain.user.UserService
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono

@Component
class AuthHandler(private val service: UserService, private val validator: RequestValidator) {

    fun register(request: ServerRequest): Mono<ServerResponse> {
        return request.bodyToMono<RegisterRequest>()
            .map { validator.validate(it) }
            .flatMap { service.register(it.email, it.displayName, it.password) }
            .flatMap { tokens ->
                ServerResponse.ok()
                    .cookie(accessTokenCookie(tokens.accessToken.value))
                    .cookie(refreshTokenCookie(tokens.refreshToken.value))
                    .bodyValue(AuthResponse(message = "SUCCESS"))
            }
    }

    fun login(request: ServerRequest): Mono<ServerResponse> {
        return request.bodyToMono<LoginRequest>()
            .map { validator.validate(it) }
            .flatMap { service.login(it.email, it.password) }
            .flatMap {  ServerResponse.ok()
                .cookie(accessTokenCookie(it.accessToken.value))
                .cookie(refreshTokenCookie(it.refreshToken.value))
                .bodyValue(AuthResponse(message = "SUCCESS")) }
    }

    private fun accessTokenCookie(value: String): ResponseCookie =
        ResponseCookie.from("access_token")
            .value(value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .build()

    private fun refreshTokenCookie(value: String): ResponseCookie =
        ResponseCookie.from("refresh_token")
            .value(value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/auth/refresh")
            .build()
}
