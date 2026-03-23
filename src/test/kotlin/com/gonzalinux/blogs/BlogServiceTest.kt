package com.gonzalinux.blogs

import com.gonzalinux.domain.post.Post
import com.gonzalinux.domain.post.PostRepository
import com.gonzalinux.domain.post.PostStatus
import com.gonzalinux.domain.post.PostTranslation
import com.gonzalinux.domain.tag.TagRepository
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

class BlogServiceTest {

    private val postRepo = mockk<PostRepository>()
    private val tagRepo = mockk<TagRepository>()
    private val registry = mockk<MeterRegistry>(relaxed = true)
    private val service = BlogService(postRepo, tagRepo, registry)

    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    private val post = Post(
        id = 1L, siteId = 1L, status = PostStatus.PUBLISHED, coverUrl = null,
        viewCount = 0L, publishedAt = now, scheduledAt = null, createdAt = now, updatedAt = now
    )

    private fun translation(body: String) = PostTranslation(
        id = 1L, postId = 1L, siteId = 1L, lang = "en",
        title = "Test", slug = "test", body = body,
        excerpt = null, createdAt = now, updatedAt = now
    )

    private fun getBySlug(body: String): BlogPostDetail? {
        every { postRepo.findPublishedBySlug(1L, "en", "test", null) } returns Mono.just(Pair(post, translation(body)))
        every { tagRepo.findByPostId(1L) } returns Flux.empty()
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation(body))
        every { postRepo.incrementViewCount(1L) } returns Mono.empty()

        var result: BlogPostDetail? = null
        StepVerifier.create(service.getBySlug(1L, "en", "test", null))
            .consumeNextWith { result = it }
            .verifyComplete()
        return result
    }

    @Test
    fun `script tags are stripped from rendered markdown`() {
        val detail = getBySlug("<script>alert('xss')</script>\n\nHello")
        assert(!detail!!.renderedBody.contains("<script>")) { "renderedBody should not contain <script>" }
        assert(!detail.renderedBody.contains("alert")) { "renderedBody should not contain alert" }
    }

    @Test
    fun `event handlers are stripped from rendered markdown`() {
        val detail = getBySlug("<img src=\"x\" onerror=\"alert(1)\"/>")
        assert(!detail!!.renderedBody.contains("onerror")) { "renderedBody should not contain onerror" }
    }

    @Test
    fun `javascript href is stripped from rendered markdown`() {
        val detail = getBySlug("<a href=\"javascript:alert(1)\">click</a>")
        assert(!detail!!.renderedBody.contains("javascript:")) { "renderedBody should not contain javascript:" }
    }

    @Test
    fun `safe html tags are preserved in rendered markdown`() {
        val detail = getBySlug("**bold** and <em>italic</em> and <a href=\"https://example.com\">link</a>")
        assert(detail!!.renderedBody.contains("<strong>")) { "renderedBody should contain <strong>" }
        assert(detail.renderedBody.contains("<em>")) { "renderedBody should contain <em>" }
        assert(detail.renderedBody.contains("href=\"https://example.com\"")) { "renderedBody should contain safe href" }
    }

    @Test
    fun `code block class attributes are preserved in rendered markdown`() {
        val detail = getBySlug("```kotlin\nval x = 1\n```")
        assert(detail!!.renderedBody.contains("class=\"language-kotlin\"")) { "renderedBody should preserve code class attribute" }
    }
}
