package com.gonzalinux.api

import com.gonzalinux.blogs.BlogService
import com.gonzalinux.blogs.buildRss
import com.gonzalinux.common.RequestContextHolder.getUserId
import com.gonzalinux.common.SiteContextHolder.getPrefix
import com.gonzalinux.common.SiteContextHolder.getSite
import com.gonzalinux.domain.site.LangConfig
import com.gonzalinux.domain.site.SiteConfig
import com.gonzalinux.utils.Utils
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import kotlin.jvm.optionals.getOrNull

@Component
class BlogsHandler(private val blogService: BlogService) {

    fun index(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            val defaultLang = site.languages.firstOrNull()?.value ?: "en"
            langPostList(defaultLang, request)
        }

    fun postList(request: ServerRequest): Mono<ServerResponse> {
        return langPostList(request.pathVariable("lang"), request)
    }

    private fun langPostList(lang: String, request: ServerRequest): Mono<ServerResponse> {
        val page = Utils.queryToInt(request.queryParam("page").getOrNull(), default = 0, min= 0)
        val tag = request.queryParam("tag").orElse(null)?.takeIf { it.isNotBlank() }
        val search = request.queryParam("search").orElse(null)?.takeIf { it.isNotBlank() }
        val size = Utils.queryToInt(request.queryParam("size").getOrNull(), default = 10, min= 1)
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            val prefix = ctx.getPrefix().let { if (it.isNotEmpty()) "/$it" else "" }
            blogService.listPublished(site.id, lang, page, size, tag, search)
                .flatMap { result ->
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .render(
                            "index", mapOf(
                                "site" to site,
                                "lang" to lang,
                                "prefix" to prefix,
                                "langConfig" to site.config.forLang(lang),
                                "posts" to result.content,
                                "currentPage" to result.page,
                                "totalPages" to result.totalPages,
                                "activeTag" to tag,
                                "search" to search,
                            )
                        )
                }
        }
    }

    fun postRoot(request: ServerRequest): Mono<ServerResponse> {
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            val defaultLang = site.languages.firstOrNull()?.value ?: "en"
            postLang(defaultLang, request)
        }
    }

    fun post(request: ServerRequest): Mono<ServerResponse> {
        val lang = request.pathVariable("lang")
       return postLang(lang, request)
    }

    private fun postLang(lang: String, request: ServerRequest): Mono<ServerResponse> {
        val slug = request.pathVariable("slug")
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            val user = ctx.getUserId()
            val prefix = ctx.getPrefix().let { if (it.isNotEmpty()) "/$it" else "" }
            blogService.getBySlug(site.id, lang, slug, user)
                .flatMap { detail ->
                    val langSlugs = detail.allTranslations.associate { it.lang to it.slug }
                    ServerResponse.ok().contentType(MediaType.TEXT_HTML)
                        .render(
                            if (user == null) "post" else "post_preview",
                            mapOf(
                                "site" to site,
                                "lang" to lang,
                                "prefix" to prefix,
                                "langConfig" to site.config.forLang(lang),
                                "post" to detail.post,
                                "translation" to detail.translation,
                                "tags" to detail.tags,
                                "renderedBody" to detail.renderedBody,
                                "codeLanguages" to detail.codeLanguages,
                                "langSlugs" to langSlugs
                            )
                        )
                }
        }
    }

    fun rssRoot(request: ServerRequest): Mono<ServerResponse> =
        Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            val defaultLang = site.languages.firstOrNull()?.value ?: "en"
            langRss(defaultLang, request)
        }

    fun rss(request: ServerRequest): Mono<ServerResponse> {
        val lang = request.pathVariable("lang")
        return langRss(lang, request)
    }

    private fun langRss(lang: String, request: ServerRequest): Mono<ServerResponse> {
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            blogService.listPublished(site.id, lang, 0, 20)
                .flatMap { result ->
                    val xml = buildRss(
                        siteTitle = site.name,
                        siteDescription = site.description ?: site.name,
                        domain = site.domain,
                        lang = lang,
                        posts = result.content,
                    )
                    ServerResponse.ok()
                        .contentType(MediaType.valueOf("application/rss+xml;charset=UTF-8"))
                        .bodyValue(xml)
                }
        }
    }

    fun postListJson(request: ServerRequest): Mono<ServerResponse> {
        val lang = request.pathVariable("lang")
        val page = Utils.queryToInt(request.queryParam("page").getOrNull(), min = 0)
        val size = Utils.queryToInt(request.queryParam("size").getOrNull(), default = 10, min = 1, max = 100)
        val tag = request.queryParam("tag").getOrNull().takeIf { !it.isNullOrEmpty() }
        val search = request.queryParam("search").getOrNull().takeIf { !it.isNullOrEmpty() }
        return Mono.deferContextual { ctx ->
            val site = ctx.getSite()!!
            blogService.listPublished(site.id, lang, page, size, tag, search)
                .flatMap { ServerResponse.ok().bodyValue(it) }
        }
    }
}

fun SiteConfig.forLang(lang: String): LangConfig = when (lang) {
    "en" -> en
    "es" -> es
    else -> LangConfig()
}

