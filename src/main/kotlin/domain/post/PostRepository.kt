package com.gonzalinux.domain.post

import com.gonzalinux.common.bindNullable
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class PostRepository(private val client: DatabaseClient) {

    fun create(siteId: Long, coverUrl: String?): Mono<Post> =
        client.sql("""
            INSERT INTO posts (site_id, cover_url)
            VALUES (:siteId, :coverUrl)
            RETURNING *
        """)
            .bind("siteId", siteId)
            .bindNullable<String>("coverUrl", coverUrl)
            .fetch().first()
            .map { mapToPost(it) }

    fun findById(id: Long, siteId: Long): Mono<Post> =
        client.sql("SELECT * FROM posts WHERE id = :id AND site_id = :siteId")
            .bind("id", id)
            .bind("siteId", siteId)
            .fetch().first()
            .map { mapToPost(it) }

    fun findAllBySiteId(siteId: Long): Flux<Post> =
        client.sql("SELECT * FROM posts WHERE site_id = :siteId ORDER BY created_at DESC")
            .bind("siteId", siteId)
            .fetch().all()
            .map { mapToPost(it) }

    fun update(id: Long, siteId: Long, coverUrl: String?, status: PostStatus?, publishedAt: OffsetDateTime?, scheduledAt: OffsetDateTime?): Mono<Post> =
        client.sql("""
            UPDATE posts SET
                cover_url    = COALESCE(:coverUrl, cover_url),
                status       = COALESCE(:status::post_status, status),
                published_at = COALESCE(:publishedAt, published_at),
                scheduled_at = COALESCE(:scheduledAt, scheduled_at),
                updated_at   = now()
            WHERE id = :id AND site_id = :siteId
            RETURNING *
        """)
            .bind("id", id)
            .bind("siteId", siteId)
            .bindNullable<String>("coverUrl", coverUrl)
            .bindNullable<String>("status", status?.name?.lowercase())
            .bindNullable<OffsetDateTime>("publishedAt", publishedAt)
            .bindNullable<OffsetDateTime>("scheduledAt", scheduledAt)
            .fetch().first()
            .map { mapToPost(it) }

    fun delete(id: Long, siteId: Long): Mono<Void> =
        client.sql("DELETE FROM posts WHERE id = :id AND site_id = :siteId")
            .bind("id", id)
            .bind("siteId", siteId)
            .then()

    fun createTranslation(postId: Long, siteId: Long, lang: String, title: String, slug: String, body: String, excerpt: String?): Mono<PostTranslation> =
        client.sql("""
            INSERT INTO post_translations (post_id, site_id, lang, title, slug, body, excerpt)
            VALUES (:postId, :siteId, :lang, :title, :slug, :body, :excerpt)
            RETURNING *
        """)
            .bind("postId", postId)
            .bind("siteId", siteId)
            .bind("lang", lang)
            .bind("title", title)
            .bind("slug", slug)
            .bind("body", body)
            .bindNullable<String>("excerpt", excerpt)
            .fetch().first()
            .map { mapToTranslation(it) }

    fun updateTranslation(postId: Long, lang: String, title: String?, slug: String?, body: String?, excerpt: String?): Mono<PostTranslation> =
        client.sql("""
            UPDATE post_translations SET
                title      = COALESCE(:title, title),
                slug       = COALESCE(:slug, slug),
                body       = COALESCE(:body, body),
                excerpt    = COALESCE(:excerpt, excerpt),
                updated_at = now()
            WHERE post_id = :postId AND lang = :lang
            RETURNING *
        """)
            .bind("postId", postId)
            .bind("lang", lang)
            .bindNullable<String>("title", title)
            .bindNullable<String>("slug", slug)
            .bindNullable<String>("body", body)
            .bindNullable<String>("excerpt", excerpt)
            .fetch().first()
            .map { mapToTranslation(it) }

    fun findTranslationsByPostId(postId: Long): Flux<PostTranslation> =
        client.sql("SELECT * FROM post_translations WHERE post_id = :postId")
            .bind("postId", postId)
            .fetch().all()
            .map { mapToTranslation(it) }

    private fun mapToPost(row: Map<String, Any>): Post = Post(
        id = row["id"] as Long,
        siteId = row["site_id"] as Long,
        status = PostStatus.valueOf((row["status"] as String).uppercase()),
        coverUrl = row["cover_url"] as? String,
        viewCount = row["view_count"] as Long,
        publishedAt = row["published_at"] as? OffsetDateTime,
        scheduledAt = row["scheduled_at"] as? OffsetDateTime,
        createdAt = row["created_at"] as OffsetDateTime,
        updatedAt = row["updated_at"] as OffsetDateTime
    )

    private fun mapToTranslation(row: Map<String, Any>): PostTranslation = PostTranslation(
        id = row["id"] as Long,
        postId = row["post_id"] as Long,
        siteId = row["site_id"] as Long,
        lang = row["lang"] as String,
        title = row["title"] as String,
        slug = row["slug"] as String,
        body = row["body"] as String,
        excerpt = row["excerpt"] as? String,
        createdAt = row["created_at"] as OffsetDateTime,
        updatedAt = row["updated_at"] as OffsetDateTime
    )
}
