package com.gonzalinux.domain.post

import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.TranslationInput
import com.gonzalinux.api.data.UpdatePostRequest
import com.gonzalinux.common.PostNotFoundException
import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.common.SlugAlreadyExistsException
import org.springframework.dao.DataIntegrityViolationException
import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteConfig
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.site.Theme
import com.gonzalinux.domain.tag.Tag
import com.gonzalinux.domain.tag.TagRepository
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PostServiceTest {

    private val postRepo = mockk<PostRepository>()
    private val siteRepo = mockk<SiteRepository>()
    private val tagRepo = mockk<TagRepository>()
    private val registry = mockk<MeterRegistry>(relaxed = true)
    private val service = PostService(postRepo, siteRepo, tagRepo, registry)

    private val now = OffsetDateTime.now(ZoneOffset.UTC)

    private val site = Site(
        id = 1L, userId = 1L, name = "My Blog", domain = "blog.example.com",
        description = null, stylesUrl = null, availableThemes = listOf(Theme.LIGHT),
        languages = listOf(Languages.ENGLISH), config = SiteConfig(),
        createdAt = now, updatedAt = now
    )

    private val post = Post(
        id = 1L, siteId = 1L, status = PostStatus.DRAFT, coverUrl = null,
        viewCount = 0L, publishedAt = null, scheduledAt = null, createdAt = now, updatedAt = now
    )

    private val translation = PostTranslation(
        id = 1L, postId = 1L, siteId = 1L, lang = "en",
        title = "Test Post", slug = "test-post", body = "Body",
        excerpt = null, createdAt = now, updatedAt = now
    )

    private val tag = Tag(id = 1L, siteId = 1L, name = "kotlin", createdAt = now)

    @Test
    fun `create creates post with translation when site exists`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Test Post", body = "Body", slug = "test-post"))
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every { postRepo.createTranslation(1L, 1L, "en", "Test Post", "test-post", "Body", null) } returns Mono.just(translation)

        StepVerifier.create(service.create(1L, 1L, request))
            .expectNext(PostWithTranslations(post, listOf(translation), emptyList()))
            .verifyComplete()
    }

    @Test
    fun `create generates slug from title when not provided`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Hello World!", body = "Body"))
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every { postRepo.createTranslation(1L, 1L, "en", "Hello World!", "hello-world", "Body", null) } returns Mono.just(translation)

        StepVerifier.create(service.create(1L, 1L, request))
            .expectNextCount(1)
            .verifyComplete()

        verify { postRepo.createTranslation(1L, 1L, "en", "Hello World!", "hello-world", "Body", null) }
    }

    @Test
    fun `create assigns tags to post when tags provided`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Test Post", body = "Body", slug = "test-post")),
            tags = listOf("kotlin")
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every { postRepo.createTranslation(1L, 1L, "en", "Test Post", "test-post", "Body", null) } returns Mono.just(translation)
        every { tagRepo.findOrCreate(1L, "kotlin") } returns Mono.just(tag)
        every { tagRepo.assignToPost(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.create(1L, 1L, request))
            .expectNext(PostWithTranslations(post, listOf(translation), listOf(tag)))
            .verifyComplete()
    }

    @Test
    fun `create throws SiteNotFoundException when site does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.create(1L, 1L, CreatePostRequest()))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `get returns post with translations when found`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { tagRepo.findByPostId(1L) } returns Flux.empty()

        StepVerifier.create(service.get(1L, 1L, 1L))
            .expectNext(PostWithTranslations(post, listOf(translation), emptyList()))
            .verifyComplete()
    }

    @Test
    fun `get throws SiteNotFoundException when site does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.get(1L, 1L, 1L))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `get throws PostNotFoundException when post does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.get(99L, 1L, 1L))
            .expectError(PostNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `delete removes post when found`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.delete(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.delete(1L, 1L, 1L))
            .verifyComplete()

        verify { postRepo.delete(1L, 1L) }
    }

    @Test
    fun `delete throws SiteNotFoundException when site does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.delete(1L, 1L, 1L))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `delete throws PostNotFoundException when post does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.delete(99L, 1L, 1L))
            .expectError(PostNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `publish sets status to PUBLISHED`() {
        val published = post.copy(status = PostStatus.PUBLISHED, publishedAt = now)
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, PostStatus.PUBLISHED, any(), null) } returns Mono.just(published)

        StepVerifier.create(service.publish(1L, 1L, 1L))
            .expectNextMatches { it.status == PostStatus.PUBLISHED }
            .verifyComplete()
    }

    @Test
    fun `publish throws PostNotFoundException when post does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.publish(99L, 1L, 1L))
            .expectError(PostNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `unpublish sets status to DRAFT`() {
        val draft = post.copy(status = PostStatus.DRAFT, publishedAt = null)
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, PostStatus.DRAFT, null, null) } returns Mono.just(draft)

        StepVerifier.create(service.unpublish(1L, 1L, 1L))
            .expectNextMatches { it.status == PostStatus.DRAFT }
            .verifyComplete()
    }

    @Test
    fun `schedule sets status to SCHEDULED with scheduledAt`() {
        val scheduledAt = now.plusDays(1)
        val scheduled = post.copy(status = PostStatus.SCHEDULED, scheduledAt = scheduledAt)
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, PostStatus.SCHEDULED, null, scheduledAt) } returns Mono.just(scheduled)

        StepVerifier.create(service.schedule(1L, 1L, 1L, scheduledAt))
            .expectNextMatches { it.status == PostStatus.SCHEDULED && it.scheduledAt == scheduledAt }
            .verifyComplete()
    }

    @Test
    fun `update replaces tags when tags provided`() {
        val updateRequest = UpdatePostRequest(tags = listOf("kotlin"))
        val updatedPost = post.copy(updatedAt = now)

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, null, null, null) } returns Mono.just(updatedPost)
        every { tagRepo.findOrCreate(1L, "kotlin") } returns Mono.just(tag)
        every { tagRepo.replacePostTags(1L, listOf(1L)) } returns Mono.empty()
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)

        StepVerifier.create(service.update(1L, 1L, 1L, updateRequest))
            .expectNextMatches { it.tags == listOf(tag) }
            .verifyComplete()
    }

    @Test
    fun `update throws PostNotFoundException when post does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.update(99L, 1L, 1L, UpdatePostRequest()))
            .expectError(PostNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `list returns page of summaries with translations and tags`() {
        val translationSummary = PostTranslationSummary(postId = 1L, lang = "en", slug = "test-post", title = "Test Post")

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.countBySiteId(1L, null, null, null) } returns Mono.just(1L)
        every { postRepo.findAllBySiteId(1L, 0, 20, null, null, null) } returns Flux.just(post)
        every { postRepo.findTranslationSummariesByPostIds(listOf(1L)) } returns Flux.just(translationSummary)
        every { tagRepo.findByPostIds(listOf(1L)) } returns Flux.just(1L to tag)

        StepVerifier.create(service.list(1L, 1L, 0, 20))
            .expectNextMatches { page ->
                page.totalElements == 1L &&
                page.content.size == 1 &&
                page.content[0].post == post &&
                page.content[0].translations == listOf(translationSummary) &&
                page.content[0].tags == listOf(tag)
            }
            .verifyComplete()
    }

    @Test
    fun `list returns empty page when no posts exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.countBySiteId(1L, null, null, null) } returns Mono.just(0L)
        every { postRepo.findAllBySiteId(1L, 0, 20, null, null, null) } returns Flux.empty()

        StepVerifier.create(service.list(1L, 1L, 0, 20))
            .expectNextMatches { page -> page.totalElements == 0L && page.content.isEmpty() }
            .verifyComplete()
    }

    @Test
    fun `list groups translations and tags correctly per post`() {
        val post2 = post.copy(id = 2L)
        val t1 = PostTranslationSummary(postId = 1L, lang = "en", slug = "post-one", title = "Post One")
        val t2 = PostTranslationSummary(postId = 2L, lang = "en", slug = "post-two", title = "Post Two")
        val tag2 = Tag(id = 2L, siteId = 1L, name = "spring", createdAt = now)

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.countBySiteId(1L, null, null, null) } returns Mono.just(2L)
        every { postRepo.findAllBySiteId(1L, 0, 20, null, null, null) } returns Flux.just(post, post2)
        every { postRepo.findTranslationSummariesByPostIds(listOf(1L, 2L)) } returns Flux.just(t1, t2)
        every { tagRepo.findByPostIds(listOf(1L, 2L)) } returns Flux.just(1L to tag, 2L to tag2)

        StepVerifier.create(service.list(1L, 1L, 0, 20))
            .expectNextMatches { page ->
                val first  = page.content[0]
                val second = page.content[1]
                first.translations == listOf(t1) && first.tags == listOf(tag) &&
                second.translations == listOf(t2) && second.tags == listOf(tag2)
            }
            .verifyComplete()
    }

    @Test
    fun `list throws SiteNotFoundException when site does not exist`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.list(1L, 1L, 0, 20))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `create throws SlugAlreadyExistsException when slug conflicts`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Test Post", body = "Body", slug = "test-post"))
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every { postRepo.createTranslation(1L, 1L, "en", "Test Post", "test-post", "Body", null) } returns
            Mono.error(DataIntegrityViolationException("duplicate key value violates unique constraint"))

        StepVerifier.create(service.create(1L, 1L, request))
            .expectError(SlugAlreadyExistsException::class.java)
            .verify()
    }

    @Test
    fun `update throws SlugAlreadyExistsException when slug conflicts`() {
        val request = UpdatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Updated Title", body = "Body", slug = "existing-slug"))
        )
        val updatedPost = post.copy(updatedAt = now)

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, null, null, null) } returns Mono.just(updatedPost)
        every { tagRepo.findByPostId(1L) } returns Flux.empty()
        every { postRepo.updateTranslation(1L, "en", "Updated Title", "existing-slug", "Body", null) } returns
            Mono.error(DataIntegrityViolationException("duplicate key value violates unique constraint"))

        StepVerifier.create(service.update(1L, 1L, 1L, request))
            .expectError(SlugAlreadyExistsException::class.java)
            .verify()
    }
}
