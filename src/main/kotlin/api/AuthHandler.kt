package com.gonzalinux.api

import com.gonzalinux.api.data.RegisterRequest
import com.gonzalinux.api.data.RegisterResponse
import com.gonzalinux.common.RequestValidator
import com.gonzalinux.domain.user.AuthTokens
import com.gonzalinux.domain.user.UserService
import org.springframework.boot.web.server.Cookie
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
                    .bodyValue(RegisterResponse(message = "SUCCESS"))
            }
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
