package com.gonzalinux.api

import com.gonzalinux.api.data.AuthResponse
import com.gonzalinux.api.data.LoginRequest
import com.gonzalinux.api.data.RegisterRequest
import com.gonzalinux.common.RequestValidator
import com.gonzalinux.common.UnauthorizedException
import com.gonzalinux.common.isSecureContext
import com.gonzalinux.domain.user.UserService
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Component
class AuthHandler(private val service: UserService, private val validator: RequestValidator) {

    companion object {
        const val REFRESH_TOKEN_COOKIE = "refresh_token"
        const val ACCESS_TOKEN_COOKIE = "access_token"
    }

    fun register(request: ServerRequest): Mono<ServerResponse> {
        val secure = request.isSecureContext()
        return request.bodyToMono<RegisterRequest>()
            .map { validator.validate(it) }
            .flatMap { service.register(it.email, it.displayName, it.password) }
            .flatMap { tokens ->
                ServerResponse.ok()
                    .cookie(accessTokenCookie(tokens.accessToken.value, secure))
                    .cookie(refreshTokenCookie(tokens.refreshToken.value, secure))
                    .bodyValue(AuthResponse(message = "SUCCESS"))
            }
    }

    fun login(request: ServerRequest): Mono<ServerResponse> {
        val secure = request.isSecureContext()
        return request.bodyToMono<LoginRequest>()
            .map { validator.validate(it) }
            .flatMap { service.login(it.email, it.password) }
            .flatMap {
                ServerResponse.ok()
                    .cookie(accessTokenCookie(it.accessToken.value, secure))
                    .cookie(refreshTokenCookie(it.refreshToken.value, secure))
                    .bodyValue(AuthResponse(message = "SUCCESS"))
            }
    }

    fun logout(request: ServerRequest): Mono<ServerResponse> {
        val token = request.cookies()[REFRESH_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: return ServerResponse.ok().build()

        return service.logout(token)
            .then(
                ServerResponse.ok()
                    .cookie(clearCookie(ACCESS_TOKEN_COOKIE))
                    .cookie(clearCookie(REFRESH_TOKEN_COOKIE))
                    .build()
            )
    }

    fun refresh(request: ServerRequest): Mono<ServerResponse> {
        val secure = request.isSecureContext()
        return request.cookies().toMono()
            .flatMap {
                val token = it[REFRESH_TOKEN_COOKIE]?.firstOrNull()
                if (token == null) {
                    Mono.error(UnauthorizedException())
                } else {
                    service.refreshToken(token.value)
                }
            }
            .flatMap {
                ServerResponse.ok()
                    .cookie(accessTokenCookie(it.accessToken.value, secure))
                    .cookie(refreshTokenCookie(it.refreshToken.value, secure))
                    .bodyValue(AuthResponse(message = "SUCCESS"))
            }
    }

    private fun clearCookie(name: String): ResponseCookie =
        ResponseCookie.from(name).value("").maxAge(0).httpOnly(true).sameSite("Strict").path("/").build()

    private fun accessTokenCookie(value: String, secure: Boolean): ResponseCookie =
        ResponseCookie.from(ACCESS_TOKEN_COOKIE)
            .value(value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .build()

    private fun refreshTokenCookie(value: String, secure: Boolean): ResponseCookie =
        ResponseCookie.from(REFRESH_TOKEN_COOKIE)
            .value(value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/auth")
            .build()
}
