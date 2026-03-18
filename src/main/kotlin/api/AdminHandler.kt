package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.api.AuthHandler.Companion.REFRESH_TOKEN_COOKIE
import com.gonzalinux.domain.user.UserService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.net.URI

@Component
class AdminHandler(private val userService: UserService) {

    fun serve(request: ServerRequest): Mono<ServerResponse> {
        val path = request.path().trimEnd('/')
        val file = when {
            path == "/admin" -> "index.html"
            path == "/admin/login" -> "login.html"
            path == "/admin/register" -> "register.html"
            path.matches(Regex(".*/sites/[^/]+/posts/[^/]+/edit")) -> "post-form.html"
            path.matches(Regex(".*/sites/[^/]+/posts/new")) -> "post-form.html"
            path.matches(Regex(".*/sites/[^/]+/posts")) -> "post-list.html"
            path.matches(Regex(".*/sites/[^/]+/edit")) -> "site-form.html"
            path == "/admin/sites/new" -> "site-form.html"
            else -> "index.html"
        }
        val resource = ClassPathResource("static/admin/$file")
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(resource)
    }

    fun logout(request: ServerRequest): Mono<ServerResponse> {
        val refreshToken = request.cookies()[REFRESH_TOKEN_COOKIE]?.firstOrNull()?.value
        val logoutMono = if (refreshToken != null) userService.logout(refreshToken) else Mono.empty<Void>()
        return logoutMono.then(
            ServerResponse.seeOther(URI.create("/admin/login"))
                .cookie(clearCookie(ACCESS_TOKEN_COOKIE))
                .cookie(clearCookie(REFRESH_TOKEN_COOKIE))
                .build()
        )
    }

    private fun clearCookie(name: String): ResponseCookie =
        ResponseCookie.from(name).value("").maxAge(0).httpOnly(true).sameSite("Strict").build()
}
