package com.gonzalinux.blogs

import com.gonzalinux.common.Page
import com.gonzalinux.common.PostNotFoundException
import com.gonzalinux.domain.post.PostRepository
import com.gonzalinux.domain.post.SitemapEntry
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.tag.TagRepository
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import io.micrometer.core.instrument.MeterRegistry
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2


@Service
class BlogService(
    private val postRepo: PostRepository,
    private val tagRepo: TagRepository,
    private val siteRepo: SiteRepository,
    private val registry: MeterRegistry
) {
    private val mdParser: Parser
    private val mdRenderer: HtmlRenderer

    private val safelist = Safelist.relaxed()
        .addAttributes("code", "class")
        .addAttributes("pre", "class")

    init {
        val options = MutableDataSet()
        mdParser = Parser.builder(options).build()
        mdRenderer = HtmlRenderer.builder(options).build()
    }

    fun listPublished(
        siteId: Long,
        lang: String,
        page: Int,
        size: Int,
        tag: String? = null,
        search: String? = null
    ): Mono<Page<BlogPostSummary>> =
        postRepo.countPublishedBySiteAndLang(siteId, lang, tag, search).zipWith(
            postRepo.findPublishedBySiteAndLang(siteId, lang, page, size, tag, search)
                .concatMap { (post, translation) ->
                    tagRepo.findByPostId(post.id).collectList()
                        .map { tags -> BlogPostSummary(post, translation, tags) }
                }
                .collectList()
        )
            .map { (total, content) ->
                Page(content, page, size, total, ((total + size - 1) / size).toInt())
            }

    fun listAllForSitemap(siteId: Long): Flux<SitemapEntry> =
        postRepo.findAllPublishedForSitemap(siteId)

    fun getBySlug(siteId: Long, lang: String, slug: String, user: Long?): Mono<BlogPostDetail> =
        postRepo.findPublishedBySlug(siteId, lang, slug, user)
            .switchIfEmpty(Mono.error(PostNotFoundException(slug = slug)))
            .flatMap { (post, translation) ->
                tagRepo.findByPostId(post.id).collectList()
                    .zipWith(postRepo.findTranslationsByPostId(post.id).collectList())
                    .map { (tags, allTranslations) ->
                        val rendered = renderMarkdown(translation.body)
                        BlogPostDetail(post, translation, tags, rendered, extractCodeLanguages(rendered), allTranslations)
                    }
            }
            .flatMap { detail ->
                postRepo.incrementViewCount(detail.post.id)
                    .doOnSuccess { registry.counter("blog.post.views", "lang", lang).increment() }
                    .thenReturn(detail)
            }

    fun getPreviewPost(siteId: Long, userId: Long, lang: String, slug: String): Mono<PreviewContext> =
        siteRepo.findById(siteId, userId)
            .flatMap { site ->
                getBySlug(site.id, lang, slug, userId)
                    .map { detail -> PreviewContext(site, detail) }
            }

    private fun renderMarkdown(markdown: String): String =
        Jsoup.clean(mdRenderer.render(mdParser.parse(markdown)), safelist)

    private fun extractCodeLanguages(html: String): List<String> =
        Jsoup.parse(html).select("code[class]")
            .flatMap { it.classNames() }
            .filter { it.startsWith("language-") }
            .map { it.removePrefix("language-") }
            .distinct()
}
