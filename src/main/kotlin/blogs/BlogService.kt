package com.gonzalinux.blogs

import com.gonzalinux.common.Page
import com.gonzalinux.common.PostNotFoundException
import com.gonzalinux.domain.post.PostEventService
import com.gonzalinux.domain.post.PostRepository
import com.gonzalinux.domain.post.SitemapEntry
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.tag.TagRepository
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import io.micrometer.core.instrument.MeterRegistry
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import java.text.Normalizer
import kotlin.math.ceil


@Service
class BlogService(
    private val postRepo: PostRepository,
    private val tagRepo: TagRepository,
    private val siteRepo: SiteRepository,
    private val postEventService: PostEventService,
    private val registry: MeterRegistry,
    private val mdParser: Parser,
    private val mdRenderer: HtmlRenderer
) {
    private companion object {
        const val WORDS_PER_MINUTE = 200.0
    }

    private val safelist = Safelist.relaxed()
        .addAttributes("code", "class")
        .addAttributes("pre", "class")

    fun listPublished(
        siteId: Long,
        lang: String,
        page: Int,
        size: Int,
        tag: String? = null,
        search: String? = null,
        sort: String? = null
    ): Mono<Page<BlogPostSummary>> =
        postRepo.countPublishedBySiteAndLang(siteId, lang, tag, search).zipWith(
            postRepo.findPublishedBySiteAndLang(siteId, lang, page, size, tag, search, sort)
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

    fun searchTags(siteId: Long, search: String?) = tagRepo.findBySiteId(siteId, search, limit = 20)

    fun getBySlug(siteId: Long, lang: String, slug: String, user: Long?): Mono<BlogPostDetail> =
        postRepo.findPublishedBySlug(siteId, lang, slug, user)
            .switchIfEmpty(Mono.error(PostNotFoundException(slug = slug)))
            .flatMap { (post, translation) ->
                tagRepo.findByPostId(post.id).collectList()
                    .zipWith(postRepo.findTranslationsByPostId(post.id).collectList())
                    .map { (tags, allTranslations) ->
                        val rendered = renderMarkdown(translation.body)
                        BlogPostDetail(
                            post,
                            translation,
                            tags,
                            rendered,
                            extractCodeLanguages(rendered),
                            allTranslations,
                            extractToc(rendered),
                            readingMinutes(rendered)
                        )
                    }
            }
            .flatMap { detail -> Mono.just(detail) }

    fun recordView(siteId: Long, lang: String, slug: String, ip: String, userAgent: String): Mono<Void> =
        postRepo.findPublishedBySlug(siteId, lang, slug)
            .flatMap { (post, _) ->
                postEventService.recordView(post.id, ip, userAgent)
                    .doOnSuccess { registry.counter("blog.post.views", "lang", lang).increment() }
            }
            .then()

    fun getPreviewPost(siteId: Long, userId: Long, lang: String, slug: String, versionId: Long?): Mono<PreviewContext> =
        siteRepo.findById(siteId, userId)
            .flatMap { site ->
                getBySlug(site.id, lang, slug, userId)
                    .flatMap { detail ->
                        postRepo.findVersionsByTranslationId(detail.translation.id).collectList()
                            .flatMap { versions ->
                                val selected = versionId?.let { id -> versions.firstOrNull { it.id == id } }
                                    ?: versions.firstOrNull { it.id == detail.translation.currentVersionId }
                                    ?: versions.maxByOrNull { it.versionNumber }
                                    ?: return@flatMap Mono.error<PreviewContext>(PostNotFoundException(slug = slug))
                                Mono.just(
                                    PreviewContext(
                                        site,
                                        detail,
                                        versions,
                                        selected,
                                        renderMarkdown(selected.body),
                                        site.role?.publish == true
                                    )
                                )
                            }
                    }
            }

    /** Sanitises the Markdown output, then gives headings anchor ids and makes images lazy-load. */
    private fun renderMarkdown(markdown: String): String {
        val doc = Jsoup.parseBodyFragment(Jsoup.clean(mdRenderer.render(mdParser.parse(markdown)), safelist))
        doc.outputSettings().prettyPrint(false)
        val body = doc.body()
        val used = mutableSetOf<String>()
        body.select("h2, h3").forEach { h ->
            val base = slugify(h.text()).ifEmpty { "section" }
            var id = base
            var n = 2
            while (!used.add(id)) id = "$base-${n++}"
            h.attr("id", id)
        }
        body.select("img").forEach { it.attr("loading", "lazy").attr("decoding", "async") }
        return body.html()
    }

    private fun extractToc(html: String): List<TocEntry> =
        Jsoup.parseBodyFragment(html).select("h2[id], h3[id]")
            .map { TocEntry(it.id(), it.text(), it.tagName().substring(1).toInt()) }
            .let { if (it.size >= 3) it else emptyList() }

    private fun readingMinutes(html: String): Int {
        val words = Jsoup.parseBodyFragment(html).text().split(Regex("\\s+")).count { it.isNotBlank() }
        return maxOf(1, ceil(words / WORDS_PER_MINUTE).toInt())
    }

    private fun slugify(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun extractCodeLanguages(html: String): List<String> =
        Jsoup.parse(html).select("code[class]")
            .flatMap { it.classNames() }
            .filter { it.startsWith("language-") }
            .map { it.removePrefix("language-") }
            .distinct()
}
