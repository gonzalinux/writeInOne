package com.gonzalinux.api

import com.gonzalinux.blogs.BlogService
import com.gonzalinux.common.RequestContextHolder.getUserId
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

@Component
class AdminHandler(
    private val blogService: BlogService,
) {

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
            path.matches(Regex(".*/sites/[^/]+/style-tester")) -> "style-tester.html"
            else -> "index.html"
        }
        val resource = ClassPathResource("static/admin/$file")
        return ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(resource)
    }

    fun preview(request: ServerRequest): Mono<ServerResponse> {
        val lang = request.pathVariable("lang")
        val slug = request.pathVariable("slug")
        val siteId = request.pathVariable("siteId").toLongOrNull()
            ?: return ServerResponse.badRequest().build()

        return Mono.deferContextual { ctx ->
            val userId = ctx.getUserId()!!
            blogService.getPreviewPost(siteId, userId, lang, slug)
                .flatMap { preview ->
                    val langSlugs = preview.detail.allTranslations.associate { it.lang to it.slug }
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .render(
                            "post_preview",
                            mapOf(
                                "site" to preview.site,
                                "lang" to lang,
                                "prefix" to "",
                                "langConfig" to preview.site.config.forLang(lang),
                                "post" to preview.detail.post,
                                "translation" to preview.detail.translation,
                                "tags" to preview.detail.tags,
                                "renderedBody" to preview.detail.renderedBody,
                                "langSlugs" to langSlugs,
                            )
                        )
                }
        }
    }

}
