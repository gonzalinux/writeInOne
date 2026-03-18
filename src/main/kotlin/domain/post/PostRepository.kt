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
                status       = COALESCE(:status, status),
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

    fun publishScheduled(): Mono<Int> =
        client.sql("""
            UPDATE posts SET status = 'published', published_at = now(), scheduled_at = null, updated_at = now()
            WHERE status = 'scheduled' AND scheduled_at <= now()
        """)
            .fetch().rowsUpdated()
            .map { it.toInt() }

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

    fun findPublishedBySiteAndLang(siteId: Long, lang: String): Flux<Pair<Post, PostTranslation>> =
        client.sql("""
            SELECT
                p.id, p.site_id, p.status, p.cover_url, p.view_count,
                p.published_at, p.scheduled_at, p.created_at, p.updated_at,
                pt.id         AS pt_id,
                pt.post_id    AS pt_post_id,
                pt.site_id    AS pt_site_id,
                pt.lang       AS pt_lang,
                pt.title      AS pt_title,
                pt.slug       AS pt_slug,
                pt.body       AS pt_body,
                pt.excerpt    AS pt_excerpt,
                pt.created_at AS pt_created_at,
                pt.updated_at AS pt_updated_at
            FROM posts p
            JOIN post_translations pt ON pt.post_id = p.id AND pt.lang = :lang AND pt.site_id = :siteId
            WHERE p.site_id = :siteId AND p.status = 'published'
            ORDER BY p.published_at DESC
        """)
            .bind("siteId", siteId)
            .bind("lang", lang)
            .fetch().all()
            .map { mapToPostAndTranslation(it) }

    fun findPublishedBySlug(siteId: Long, lang: String, slug: String): Mono<Pair<Post, PostTranslation>> =
        client.sql("""
            SELECT
                p.id, p.site_id, p.status, p.cover_url, p.view_count,
                p.published_at, p.scheduled_at, p.created_at, p.updated_at,
                pt.id         AS pt_id,
                pt.post_id    AS pt_post_id,
                pt.site_id    AS pt_site_id,
                pt.lang       AS pt_lang,
                pt.title      AS pt_title,
                pt.slug       AS pt_slug,
                pt.body       AS pt_body,
                pt.excerpt    AS pt_excerpt,
                pt.created_at AS pt_created_at,
                pt.updated_at AS pt_updated_at
            FROM posts p
            JOIN post_translations pt ON pt.post_id = p.id AND pt.lang = :lang AND pt.site_id = :siteId
            WHERE p.site_id = :siteId AND p.status = 'published' AND pt.slug = :slug
        """)
            .bind("siteId", siteId)
            .bind("lang", lang)
            .bind("slug", slug)
            .fetch().first()
            .map { mapToPostAndTranslation(it) }

    fun incrementViewCount(postId: Long): Mono<Void> =
        client.sql("UPDATE posts SET view_count = view_count + 1 WHERE id = :id")
            .bind("id", postId)
            .then()

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

    private fun mapToPostAndTranslation(row: Map<String, Any>): Pair<Post, PostTranslation> =
        Post(
            id = row["id"] as Long,
            siteId = row["site_id"] as Long,
            status = PostStatus.valueOf((row["status"] as String).uppercase()),
            coverUrl = row["cover_url"] as? String,
            viewCount = row["view_count"] as Long,
            publishedAt = row["published_at"] as? OffsetDateTime,
            scheduledAt = row["scheduled_at"] as? OffsetDateTime,
            createdAt = row["created_at"] as OffsetDateTime,
            updatedAt = row["updated_at"] as OffsetDateTime
        ) to PostTranslation(
            id = row["pt_id"] as Long,
            postId = row["pt_post_id"] as Long,
            siteId = row["pt_site_id"] as Long,
            lang = row["pt_lang"] as String,
            title = row["pt_title"] as String,
            slug = row["pt_slug"] as String,
            body = row["pt_body"] as String,
            excerpt = row["pt_excerpt"] as? String,
            createdAt = row["pt_created_at"] as OffsetDateTime,
            updatedAt = row["pt_updated_at"] as OffsetDateTime
        )
}
