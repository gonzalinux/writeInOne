package com.gonzalinux.domain.post

import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.TranslationInput
import com.gonzalinux.api.data.UpdatePostRequest
import com.gonzalinux.common.ForbiddenException
import com.gonzalinux.common.PostNotFoundException
import com.gonzalinux.common.PostVersionNotFoundException
import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.common.SlugAlreadyExistsException
import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.site.Roles
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.site.SiteConfig
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.site.SiteStatus
import com.gonzalinux.domain.site.Theme
import com.gonzalinux.domain.tag.Tag
import com.gonzalinux.domain.tag.TagRepository
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
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
        prefix = "", description = null, stylesUrl = null, availableThemes = listOf(Theme.LIGHT),
        languages = listOf(Languages.ENGLISH), config = SiteConfig(), status = SiteStatus.NOT_VERIFIED,
        createdAt = now, updatedAt = now, verifyDate = now, role = Roles.ADMIN
    )

    private val post = Post(
        id = 1L, siteId = 1L, status = PostStatus.DRAFT, coverUrl = null,
        viewCount = 0L, publishedAt = null, scheduledAt = null, createdAt = now, updatedAt = now
    )

    private val translation = PostTranslation(
        id = 1L, postId = 1L, siteId = 1L, lang = "en",
        title = "Test Post", slug = "test-post", body = "Body",
        excerpt = null, currentVersionId = null, createdAt = now, updatedAt = now
    )

    private val version = PostTranslationVersion(
        id = 1L, postTranslationId = 1L, versionNumber = 1, status = VersionStatus.DRAFT,
        title = "Test Post", slug = "test-post", body = "Body", excerpt = null,
        authorId = 1L, createdAt = now, publishedAt = null, updatedAt = now
    )

    private val tag = Tag(id = 1L, siteId = 1L, name = "kotlin", createdAt = now)

    @Test
    fun `create creates post with translation when site exists`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Test Post", body = "Body", slug = "test-post"))
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every {
            postRepo.createTranslationShell(1L, 1L, "en", "Test Post", "test-post", "Body", null)
        } returns Mono.just(translation)
        every {
            postRepo.createVersion(1L, "Test Post", "test-post", "Body", null, 1L)
        } returns Mono.just(version)
        every { postRepo.findLatestVersionsByPostId(1L) } returns Flux.just("en" to version)

        StepVerifier.create(service.create(1L, 1L, request))
            .expectNext(PostWithTranslations(post, listOf(translation), emptyList(), mapOf("en" to version)))
            .verifyComplete()
    }

    @Test
    fun `create generates slug from title when not provided`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Hello World!", body = "Body"))
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every {
            postRepo.createTranslationShell(1L, 1L, "en", "Hello World!", "hello-world", "Body", null)
        } returns Mono.just(translation)
        every {
            postRepo.createVersion(1L, "Hello World!", "hello-world", "Body", null, 1L)
        } returns Mono.just(version)
        every { postRepo.findLatestVersionsByPostId(1L) } returns Flux.just("en" to version)

        StepVerifier.create(service.create(1L, 1L, request))
            .expectNextCount(1)
            .verifyComplete()

        verify { postRepo.createTranslationShell(1L, 1L, "en", "Hello World!", "hello-world", "Body", null) }
    }

    @Test
    fun `create assigns tags to post when tags provided`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Test Post", body = "Body", slug = "test-post")),
            tags = listOf("kotlin")
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every {
            postRepo.createTranslationShell(1L, 1L, "en", "Test Post", "test-post", "Body", null)
        } returns Mono.just(translation)
        every {
            postRepo.createVersion(1L, "Test Post", "test-post", "Body", null, 1L)
        } returns Mono.just(version)
        every { tagRepo.findOrCreate(1L, "kotlin") } returns Mono.just(tag)
        every { tagRepo.assignToPost(1L, 1L) } returns Mono.empty()
        every { postRepo.findLatestVersionsByPostId(1L) } returns Flux.just("en" to version)

        StepVerifier.create(service.create(1L, 1L, request))
            .expectNext(PostWithTranslations(post, listOf(translation), listOf(tag), mapOf("en" to version)))
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
    fun `get returns post with translations and latest versions when found`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { tagRepo.findByPostId(1L) } returns Flux.empty()
        every { postRepo.findLatestVersionsByPostId(1L) } returns Flux.just("en" to version)

        StepVerifier.create(service.get(1L, 1L, 1L))
            .expectNext(PostWithTranslations(post, listOf(translation), emptyList(), mapOf("en" to version)))
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
    fun `getPublished returns post and translation when found`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findPublishedBySlug(1L, "en", "test-post") } returns Mono.just(post to translation)

        StepVerifier.create(service.getPublished(1L, "en", "test-post", 1L))
            .expectNext(post to translation)
            .verifyComplete()
    }

    @Test
    fun `getPublished throws SiteNotFoundException when caller is not a member`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.empty()

        StepVerifier.create(service.getPublished(1L, "en", "test-post", 1L))
            .expectError(SiteNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `getPublished throws PostNotFoundException when no published post has that slug`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findPublishedBySlug(1L, "en", "missing") } returns Mono.empty()

        StepVerifier.create(service.getPublished(1L, "en", "missing", 1L))
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
    fun `publish sets status to PUBLISHED and publishes translations that never went live`() {
        val published = post.copy(status = PostStatus.PUBLISHED, publishedAt = now)
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, PostStatus.PUBLISHED, any(), null) } returns Mono.just(published)
        every { postRepo.publishInitialVersions(1L) } returns Mono.empty()

        StepVerifier.create(service.publish(1L, 1L, 1L))
            .expectNextMatches { it.status == PostStatus.PUBLISHED }
            .verifyComplete()

        verify { postRepo.publishInitialVersions(1L) }
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
        every { postRepo.findLatestVersionsByPostId(1L) } returns Flux.empty()

        StepVerifier.create(service.update(1L, 1L, 1L, updateRequest))
            .expectNextMatches { it.tags == listOf(tag) }
            .verifyComplete()
    }

    @Test
    fun `update creates a new draft version for an existing translation`() {
        val updateRequest = UpdatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Updated", body = "New body", slug = "test-post"))
        )
        val updatedPost = post.copy(updatedAt = now)
        val newVersion = version.copy(id = 2L, versionNumber = 2, title = "Updated", body = "New body")

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, null, null, null) } returns Mono.just(updatedPost)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { tagRepo.findByPostId(1L) } returns Flux.empty()
        every {
            postRepo.createVersion(1L, "Updated", "test-post", "New body", null, 1L)
        } returns Mono.just(newVersion)
        every { postRepo.pruneOldDrafts(1L) } returns Mono.empty()
        every { postRepo.findLatestVersionsByPostId(1L) } returns Flux.just("en" to newVersion)

        StepVerifier.create(service.update(1L, 1L, 1L, updateRequest))
            .expectNextMatches { it.latestVersions["en"] == newVersion }
            .verifyComplete()

        verify { postRepo.createVersion(1L, "Updated", "test-post", "New body", null, 1L) }
        verify { postRepo.pruneOldDrafts(1L) }
    }

    @Test
    fun `update creates the initial draft when adding a new language to an existing post`() {
        val updateRequest = UpdatePostRequest(
            translations = mapOf("es" to TranslationInput(title = "Hola", body = "Cuerpo", slug = "hola"))
        )
        val updatedPost = post.copy(updatedAt = now)
        val esTranslation = translation.copy(id = 2L, lang = "es", title = "Hola", slug = "hola", body = "Cuerpo")
        val esVersion = version.copy(id = 2L, postTranslationId = 2L, title = "Hola", slug = "hola", body = "Cuerpo")

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.update(1L, 1L, null, null, null, null) } returns Mono.just(updatedPost)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation) andThen Flux.just(
            translation,
            esTranslation
        )
        every { tagRepo.findByPostId(1L) } returns Flux.empty()
        every {
            postRepo.createTranslationShell(1L, 1L, "es", "Hola", "hola", "Cuerpo", null)
        } returns Mono.just(esTranslation)
        every {
            postRepo.createVersion(2L, "Hola", "hola", "Cuerpo", null, 1L)
        } returns Mono.just(esVersion)
        every { postRepo.findLatestVersionsByPostId(1L) } returns Flux.just("es" to esVersion)

        StepVerifier.create(service.update(1L, 1L, 1L, updateRequest))
            .expectNextMatches { it.translations.size == 2 && it.latestVersions["es"] == esVersion }
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
    fun `listVersions returns version history for a translation`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { postRepo.findVersionsByTranslationId(1L) } returns Flux.just(version)

        StepVerifier.create(service.listVersions(1L, 1L, 1L, "en"))
            .expectNext(version)
            .verifyComplete()
    }

    @Test
    fun `getVersion returns a specific version`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { postRepo.findVersionById(1L, 1L) } returns Mono.just(version)

        StepVerifier.create(service.getVersion(1L, 1L, 1L, "en", 1L))
            .expectNext(version)
            .verifyComplete()
    }

    @Test
    fun `getVersion throws PostVersionNotFoundException when version does not belong to translation`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { postRepo.findVersionById(99L, 1L) } returns Mono.empty()

        StepVerifier.create(service.getVersion(1L, 1L, 1L, "en", 99L))
            .expectError(PostVersionNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `publishVersion copies version content into the live translation`() {
        val published = translation.copy(currentVersionId = 1L)
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { postRepo.findVersionById(1L, 1L) } returns Mono.just(version)
        every { postRepo.publishVersion(1L, 1L) } returns Mono.just(published)
        every { postRepo.update(1L, 1L, null, PostStatus.PUBLISHED, any(), null) } returns
            Mono.just(post.copy(status = PostStatus.PUBLISHED, publishedAt = now))

        StepVerifier.create(service.publishVersion(1L, 1L, 1L, "en", 1L))
            .expectNext(published)
            .verifyComplete()
    }

    @Test
    fun `publishVersion does not re-publish the post when it's already live`() {
        val publishedPost = post.copy(status = PostStatus.PUBLISHED, publishedAt = now)
        val published = translation.copy(currentVersionId = 1L)
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(publishedPost)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { postRepo.findVersionById(1L, 1L) } returns Mono.just(version)
        every { postRepo.publishVersion(1L, 1L) } returns Mono.just(published)

        StepVerifier.create(service.publishVersion(1L, 1L, 1L, "en", 1L))
            .expectNext(published)
            .verifyComplete()

        verify(exactly = 0) { postRepo.update(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `publishVersion is forbidden for a writer`() {
        val writerSite = site.copy(role = Roles.WRITER)
        every { siteRepo.findById(1L, 1L) } returns Mono.just(writerSite)

        StepVerifier.create(service.publishVersion(1L, 1L, 1L, "en", 1L))
            .expectError(ForbiddenException::class.java)
            .verify()
    }

    @Test
    fun `publishVersion maps a slug conflict to SlugAlreadyExistsException`() {
        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.findById(1L, 1L) } returns Mono.just(post)
        every { postRepo.findTranslationsByPostId(1L) } returns Flux.just(translation)
        every { postRepo.findVersionById(1L, 1L) } returns Mono.just(version)
        every { postRepo.publishVersion(1L, 1L) } returns
                Mono.error(DataIntegrityViolationException("duplicate key value violates unique constraint"))

        StepVerifier.create(service.publishVersion(1L, 1L, 1L, "en", 1L))
            .expectError(SlugAlreadyExistsException::class.java)
            .verify()
    }

    @Test
    fun `list returns page of summaries with translations and tags`() {
        val translationSummary =
            PostTranslationSummary(postId = 1L, lang = "en", slug = "test-post", title = "Test Post")

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
                val first = page.content[0]
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
    fun `create throws SlugAlreadyExistsException when the translation shell insert conflicts`() {
        val request = CreatePostRequest(
            translations = mapOf("en" to TranslationInput(title = "Test Post", body = "Body", slug = "test-post"))
        )

        every { siteRepo.findById(1L, 1L) } returns Mono.just(site)
        every { postRepo.create(1L, null) } returns Mono.just(post)
        every {
            postRepo.createTranslationShell(1L, 1L, "en", "Test Post", "test-post", "Body", null)
        } returns Mono.error(DataIntegrityViolationException("duplicate key value violates unique constraint"))

        StepVerifier.create(service.create(1L, 1L, request))
            .expectError(SlugAlreadyExistsException::class.java)
            .verify()
    }
}
