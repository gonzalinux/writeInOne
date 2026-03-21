package com.gonzalinux.domain.post

import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.UpdatePostRequest
import com.gonzalinux.common.Page
import com.gonzalinux.common.PostNotFoundException
import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.common.SlugAlreadyExistsException
import org.springframework.dao.DataIntegrityViolationException
import com.gonzalinux.domain.site.SiteRepository
import com.gonzalinux.domain.tag.Tag
import com.gonzalinux.domain.tag.TagRepository
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import java.time.OffsetDateTime

private val logger = KotlinLogging.logger {}


@Service
class PostService(private val postRepo: PostRepository, private val siteRepo: SiteRepository, private val tagRepo: TagRepository, private val registry: MeterRegistry) {

    fun create(siteId: Long, userId: Long, request: CreatePostRequest): Mono<PostWithTranslations> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.create(siteId, request.coverUrl) }
            .flatMap { post ->
                val saveTranslations = request.translations.entries.map { (lang, input) ->
                    val slug = input.slug ?: generateSlug(input.title)
                    postRepo.createTranslation(post.id, siteId, lang, input.title, slug, input.body, input.excerpt)
                }
                val saveTags: Mono<List<Tag>> = if (request.tags.isEmpty()) {
                    Mono.just(emptyList())
                } else {
                    Flux.merge(request.tags.map { tagRepo.findOrCreate(siteId, it) }).collectList()
                }
                Flux.merge(saveTranslations).collectList()
                    .onErrorMap(DataIntegrityViolationException::class.java) { e ->
                        val slug = request.translations.values.firstOrNull()?.slug
                            ?: generateSlug(request.translations.values.first().title)
                        SlugAlreadyExistsException(slug)
                    }
                    .zipWith(saveTags)
                    .flatMap { (translations, tags) ->
                        Flux.merge(tags.map { tagRepo.assignToPost(post.id, it.id) }).then(
                            Mono.just(PostWithTranslations(post, translations, tags))
                        )
                    }
                    .doOnSuccess {
                        logger.info { "Post created [postId=${post.id}, siteId=$siteId]" }
                        registry.counter("posts.created").increment()
                    }
            }

    fun get(id: Long, siteId: Long, userId: Long): Mono<PostWithTranslations> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { post -> postWithTranslationsAndTags(post) }

    fun list(siteId: Long, userId: Long, page: Int, size: Int, status: String? = null, tag: String? = null, search: String? = null): Mono<Page<PostSummary>> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap {
                postRepo.countBySiteId(siteId, status, tag, search).zipWith(
                    postRepo.findAllBySiteId(siteId, page, size, status, tag, search).collectList()
                )
            }
            .flatMap { (total, posts) ->
                if (posts.isEmpty()) {
                    Mono.just(Page(emptyList(), page, size, total, 0))
                } else {
                    val ids = posts.map { it.id }
                    postRepo.findTranslationSummariesByPostIds(ids).collectList()
                        .zipWith(tagRepo.findByPostIds(ids).collectList())
                        .map { (translations, tagPairs) ->
                            val translsByPost = translations.groupBy { it.postId }
                            val tagsByPost    = tagPairs.groupBy({ it.first }, { it.second })
                            val content = posts.map { post ->
                                PostSummary(post, translsByPost[post.id] ?: emptyList(), tagsByPost[post.id] ?: emptyList())
                            }
                            Page(content, page, size, total, ((total + size - 1) / size).toInt())
                        }
                }
            }

    fun update(id: Long, siteId: Long, userId: Long, request: UpdatePostRequest): Mono<PostWithTranslations> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { post ->
                postRepo.update(post.id, siteId, request.coverUrl, null, null, null)
                    .flatMap { updated ->
                        val updateTranslations = request.translations?.entries?.map { (lang, input) ->
                            val slug = input.slug ?: generateSlug(input.title)
                            postRepo.updateTranslation(post.id, lang, input.title, slug, input.body, input.excerpt)
                        } ?: emptyList()
                        val tagsStep: Mono<List<Tag>> = if (request.tags != null) {
                            Flux.fromIterable(request.tags).flatMap { tagRepo.findOrCreate(siteId, it) }
                                .collectList()
                                .flatMap { tags ->
                                    tagRepo.replacePostTags(post.id, tags.map { it.id }).thenReturn(tags)
                                }
                        } else {
                            tagRepo.findByPostId(post.id).collectList()
                        }
                        Flux.merge(updateTranslations).collectList()
                            .onErrorMap(DataIntegrityViolationException::class.java) { _ ->
                                val slug = request.translations?.values?.firstOrNull()?.slug
                                    ?: request.translations?.values?.firstOrNull()?.let { generateSlug(it.title) }
                                    ?: "provided"
                                SlugAlreadyExistsException(slug)
                            }
                            .flatMap { postRepo.findTranslationsByPostId(updated.id).collectList() }
                            .zipWith(tagsStep)
                            .map { (translations, tags) -> PostWithTranslations(updated, translations, tags) }
                    }
            }

    fun delete(id: Long, siteId: Long, userId: Long): Mono<Void> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { postRepo.delete(id, siteId) }
            .doOnSuccess { registry.counter("posts.deleted").increment() }

    fun publish(id: Long, siteId: Long, userId: Long): Mono<Post> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { postRepo.update(id, siteId, null, PostStatus.PUBLISHED, OffsetDateTime.now(), null) }
        .doOnSuccess {
            logger.info { "Post published [postId=$id, siteId=$siteId]" }
            registry.counter("posts.published", "source", "manual").increment()
        }

    fun unpublish(id: Long, siteId: Long, userId: Long): Mono<Post> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { postRepo.update(id, siteId, null, PostStatus.DRAFT, null, null) }
            .doOnSuccess {
                logger.info { "Post unpublished [postId=$id, siteId=$siteId]" }
                registry.counter("posts.unpublished").increment()
            }

    fun schedule(id: Long, siteId: Long, userId: Long, scheduledAt: OffsetDateTime): Mono<Post> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { postRepo.update(id, siteId, null, PostStatus.SCHEDULED, null, scheduledAt) }
            .doOnSuccess {
                logger.info { "Post scheduled [postId=$id, siteId=$siteId, scheduledAt=$scheduledAt]" }
                registry.counter("posts.scheduled").increment()
            }

    private fun postWithTranslationsAndTags(post: Post): Mono<PostWithTranslations> =
        postRepo.findTranslationsByPostId(post.id).collectList()
            .zipWith(tagRepo.findByPostId(post.id).collectList())
            .map { (translations, tags) -> PostWithTranslations(post, translations, tags) }

    private fun generateSlug(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
}
