package com.gonzalinux.domain.post

import com.gonzalinux.api.data.CreatePostRequest
import com.gonzalinux.api.data.UpdatePostRequest
import com.gonzalinux.common.PostNotFoundException
import com.gonzalinux.common.SiteNotFoundException
import com.gonzalinux.domain.site.SiteRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

private val logger = KotlinLogging.logger {}

data class PostWithTranslations(val post: Post, val translations: List<PostTranslation>)

@Service
class PostService(private val postRepo: PostRepository, private val siteRepo: SiteRepository) {

    fun create(siteId: Long, userId: Long, request: CreatePostRequest): Mono<PostWithTranslations> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.create(siteId, request.coverUrl) }
            .flatMap { post ->
                val saveTranslations = request.translations.entries.map { (lang, input) ->
                    val slug = input.slug ?: generateSlug(input.title)
                    postRepo.createTranslation(post.id, siteId, lang, input.title, slug, input.body, input.excerpt)
                }
                Flux.merge(saveTranslations)
                    .collectList()
                    .map { PostWithTranslations(post, it) }
                    .doOnSuccess { logger.info { "Post created [postId=${post.id}, siteId=$siteId]" } }
            }

    fun get(id: Long, siteId: Long, userId: Long): Mono<PostWithTranslations> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { post ->
                postRepo.findTranslationsByPostId(post.id)
                    .collectList()
                    .map { PostWithTranslations(post, it) }
            }

    fun list(siteId: Long, userId: Long): Flux<PostWithTranslations> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMapMany { postRepo.findAllBySiteId(siteId) }
            .flatMap { post ->
                postRepo.findTranslationsByPostId(post.id)
                    .collectList()
                    .map { PostWithTranslations(post, it) }
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
                        Flux.merge(updateTranslations)
                            .collectList()
                            .flatMap { postRepo.findTranslationsByPostId(updated.id).collectList() }
                            .map { PostWithTranslations(updated, it) }
                    }
            }

    fun delete(id: Long, siteId: Long, userId: Long): Mono<Void> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { postRepo.delete(id, siteId) }

    fun publish(id: Long, siteId: Long, userId: Long): Mono<Post> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { postRepo.update(id, siteId, null, PostStatus.PUBLISHED, OffsetDateTime.now(), null) }
        .doOnSuccess { logger.info { "Post published [postId=$id, siteId=$siteId]" } }

    fun schedule(id: Long, siteId: Long, userId: Long, scheduledAt: OffsetDateTime): Mono<Post> =
        siteRepo.findById(siteId, userId)
            .switchIfEmpty(Mono.error(SiteNotFoundException(siteId)))
            .flatMap { postRepo.findById(id, siteId) }
            .switchIfEmpty(Mono.error(PostNotFoundException(id)))
            .flatMap { postRepo.update(id, siteId, null, PostStatus.DRAFT, null, scheduledAt) }
            .doOnSuccess { logger.info { "Post scheduled [postId=$id, siteId=$siteId, scheduledAt=$scheduledAt]" } }

    private fun generateSlug(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
}
