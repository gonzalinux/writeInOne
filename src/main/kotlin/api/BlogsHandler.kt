package com.gonzalinux.api

import com.gonzalinux.blogs.BlogPostSummary
import com.gonzalinux.blogs.BlogService
import com.gonzalinux.common.SiteContextHolder.getSite
import com.gonzalinux.domain.site.LangConfig
import com.gonzalinux.domain.site.SiteConfig
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Mono
import java.net.URI

@Component
class BlogsHandler(private val blogService: BlogService) {

    fun index(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            val defaultLang = site.languages.firstOrNull()?.value ?: "en"
            ServerResponse.permanentRedirect(URI.create("/$defaultLang")).build()
        }

    fun postList(request: ServerRequest): Mono<ServerResponse> {
        val lang = request.pathVariable("lang")
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            blogService.listPublished(site.id, lang)
                .collectList()
                .flatMap { posts ->
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .render("index", mapOf(
                            "site" to site,
                            "lang" to lang,
                            "langConfig" to site.config.forLang(lang),
                            "posts" to posts
                        ))
                }
        }
    }

    fun post(request: ServerRequest): Mono<ServerResponse> {
        val lang = request.pathVariable("lang")
        val slug = request.pathVariable("slug")
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            blogService.getBySlug(site.id, lang, slug)
                .flatMap { detail ->
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .render("post", mapOf(
                            "site" to site,
                            "lang" to lang,
                            "langConfig" to site.config.forLang(lang),
                            "post" to detail.post,
                            "translation" to detail.translation,
                            "tags" to detail.tags,
                            "renderedBody" to detail.renderedBody
                        ))
                }
        }
    }

    fun postListJson(request: ServerRequest): Mono<ServerResponse> {
        val lang = request.pathVariable("lang")
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            ServerResponse.ok().body<BlogPostSummary>(blogService.listPublished(site.id, lang))
        }
    }
}

private fun SiteConfig.forLang(lang: String): LangConfig = when (lang) {
    "en" -> en
    "es" -> es
    else -> LangConfig()
}
