package com.gonzalinux.blogs

import com.gonzalinux.common.PostNotFoundException
import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.post.Post
import com.gonzalinux.domain.post.PostRepository
import com.gonzalinux.domain.post.PostTranslation
import com.gonzalinux.domain.tag.Tag
import com.gonzalinux.domain.tag.TagRepository
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@Service
class BlogService(
    private val postRepo: PostRepository,
    private val tagRepo: TagRepository
) {
    private val mdParser: Parser
    private val mdRenderer: HtmlRenderer

    init {
        val options = MutableDataSet()
        mdParser = Parser.builder(options).build()
        mdRenderer = HtmlRenderer.builder(options).build()
    }

    fun listPublished(siteId: Long, lang: String): Flux<BlogPostSummary> =
        postRepo.findPublishedBySiteAndLang(siteId, lang)
            .flatMap { (post, translation) ->
                tagRepo.findByPostId(post.id).collectList()
                    .map { tags -> BlogPostSummary(post, translation, tags) }
            }

    fun getBySlug(siteId: Long, lang: String, slug: String): Mono<BlogPostDetail> =
        postRepo.findPublishedBySlug(siteId, lang, slug)
            .switchIfEmpty(Mono.error(PostNotFoundException(0)))
            .flatMap { (post, translation) ->
                tagRepo.findByPostId(post.id).collectList()
                    .map { tags -> BlogPostDetail(post, translation, tags, renderMarkdown(translation.body)) }
            }
            .flatMap { detail -> postRepo.incrementViewCount(detail.post.id).thenReturn(detail) }

    private fun renderMarkdown(markdown: String): String =
        mdRenderer.render(mdParser.parse(markdown))
}
