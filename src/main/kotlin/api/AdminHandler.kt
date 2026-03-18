package com.gonzalinux.api

import com.gonzalinux.api.AuthHandler.Companion.ACCESS_TOKEN_COOKIE
import com.gonzalinux.api.AuthHandler.Companion.REFRESH_TOKEN_COOKIE
import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.CreateSiteRequest
import com.gonzalinux.api.data.TranslationInput
import com.gonzalinux.common.RequestContextHolder.getRequestContext
import com.gonzalinux.common.isSecureContext
import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.post.PostService
import com.gonzalinux.domain.post.PostWithTranslations
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteService
import com.gonzalinux.domain.user.UserService
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import java.net.URI

@Component
class AdminHandler(
    private val userService: UserService,
    private val siteService: SiteService,
    private val postService: PostService
) {

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun loginPage(request: ServerRequest): Mono<ServerResponse> =
        ServerResponse.ok().contentType(MediaType.TEXT_HTML).render("admin/login")

    fun loginSubmit(request: ServerRequest): Mono<ServerResponse> {
        val secure = request.isSecureContext()
        return request.formData().flatMap { form ->
            val email = form.getFirst("email").orEmpty()
            val password = form.getFirst("password").orEmpty()
            userService.login(email, password)
                .flatMap { tokens ->
                    ServerResponse.seeOther(URI.create("/admin"))
                        .cookie(accessTokenCookie(tokens.accessToken.value, secure))
                        .cookie(refreshTokenCookie(tokens.refreshToken.value, secure))
                        .build()
                }
        }
    }

    fun registerPage(request: ServerRequest): Mono<ServerResponse> =
        ServerResponse.ok().contentType(MediaType.TEXT_HTML).render("admin/register")

    fun registerSubmit(request: ServerRequest): Mono<ServerResponse> {
        val secure = request.isSecureContext()
        return request.formData().flatMap { form ->
            val email = form.getFirst("email").orEmpty()
            val displayName = form.getFirst("displayName").orEmpty()
            val password = form.getFirst("password").orEmpty()
            userService.register(email, displayName, password)
                .flatMap { tokens ->
                    ServerResponse.seeOther(URI.create("/admin"))
                        .cookie(accessTokenCookie(tokens.accessToken.value, secure))
                        .cookie(refreshTokenCookie(tokens.refreshToken.value, secure))
                        .build()
                }
        }
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

    // ── Dashboard ────────────────────────────────────────────────────────────

    fun dashboard(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val userId = ctx.getRequestContext()!!.userId
            siteService.list(userId).collectList()
                .flatMap { sites ->
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .render("admin/dashboard", mapOf("sites" to sites))
                }
        }

    // ── Sites ────────────────────────────────────────────────────────────────

    fun newSitePage(request: ServerRequest): Mono<ServerResponse> =
        ServerResponse.ok().contentType(MediaType.TEXT_HTML).render("admin/site-form")

    fun createSite(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val userId = ctx.getRequestContext()!!.userId
            request.formData().flatMap { form ->
                val createRequest = CreateSiteRequest(
                    name = form.getFirst("name").orEmpty(),
                    domain = form.getFirst("domain").orEmpty(),
                    description = form.getFirst("description")?.takeIf { it.isNotBlank() },
                    languages = listOf(Languages.ENGLISH)
                )
                siteService.create(userId, createRequest)
                    .flatMap { ServerResponse.seeOther(URI.create("/admin")).build() }
            }
        }

    // ── Posts ────────────────────────────────────────────────────────────────

    fun postListPage(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val userId = ctx.getRequestContext()!!.userId
            val siteId = request.pathVariable("siteId").toLong()
            postService.list(siteId, userId).collectList()
                .flatMap { posts ->
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .render("admin/post-list", mapOf(
                            "siteId" to siteId,
                            "posts" to posts
                        ))
                }
        }

    fun newPostPage(request: ServerRequest): Mono<ServerResponse> {
        val siteId = request.pathVariable("siteId").toLong()
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML)
            .render("admin/post-form", mapOf(
                "siteId" to siteId,
                "languages" to Languages.entries
            ))
    }

    fun createPost(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val userId = ctx.getRequestContext()!!.userId
            val siteId = request.pathVariable("siteId").toLong()
            request.formData().flatMap { form ->
                val lang = form.getFirst("lang") ?: Languages.ENGLISH.value
                val tags = form.getFirst("tags").orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val createRequest = CreatePostRequest(
                    coverUrl = form.getFirst("coverUrl")?.takeIf { it.isNotBlank() },
                    translations = mapOf(
                        lang to TranslationInput(
                            title = form.getFirst("title").orEmpty(),
                            body = form.getFirst("body").orEmpty(),
                            slug = form.getFirst("slug")?.takeIf { it.isNotBlank() },
                            excerpt = form.getFirst("excerpt")?.takeIf { it.isNotBlank() }
                        )
                    ),
                    tags = tags
                )
                postService.create(siteId, userId, createRequest)
                    .flatMap { ServerResponse.seeOther(URI.create("/admin/sites/$siteId/posts")).build() }
            }
        }

    // ── Cookies ──────────────────────────────────────────────────────────────

    private fun accessTokenCookie(value: String, secure: Boolean): ResponseCookie =
        ResponseCookie.from(ACCESS_TOKEN_COOKIE)
            .value(value).httpOnly(true).secure(secure).sameSite("Strict").build()

    private fun refreshTokenCookie(value: String, secure: Boolean): ResponseCookie =
        ResponseCookie.from(REFRESH_TOKEN_COOKIE)
            .value(value).httpOnly(true).secure(secure).sameSite("Strict")
            .path("/auth/refresh").build()

    private fun clearCookie(name: String): ResponseCookie =
        ResponseCookie.from(name).value("").maxAge(0).httpOnly(true).sameSite("Strict").build()
}
