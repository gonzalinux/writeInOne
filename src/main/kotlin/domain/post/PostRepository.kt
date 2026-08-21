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
        client.sql(
            """
            INSERT INTO posts (site_id, cover_url)
            VALUES (:siteId, :coverUrl)
            RETURNING *
        """
        )
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

    fun findAllBySiteId(
        siteId: Long,
        page: Int,
        size: Int,
        status: String? = null,
        tags: List<String>? = null,
        search: String? = null
    ): Flux<Post> {
        val (sql, spec) = buildAdminQuery(
            "SELECT DISTINCT p.*",
            "ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset",
            siteId,
            status,
            tags,
            search
        )
        return spec(client.sql(sql))
            .bind("limit", size).bind("offset", page * size)
            .fetch().all().map { mapToPost(it) }
    }

    fun countBySiteId(siteId: Long, status: String? = null, tags: List<String>? = null, search: String? = null): Mono<Long> {
        val (sql, spec) = buildAdminQuery("SELECT COUNT(DISTINCT p.id)", "", siteId, status, tags, search)
        return spec(client.sql(sql)).fetch().first().map { it["count"] as Long }
    }

    private fun buildAdminQuery(
        select: String,
        tail: String,
        siteId: Long,
        status: String?,
        tags: List<String>?,
        search: String?
    ): Pair<String, (DatabaseClient.GenericExecuteSpec) -> DatabaseClient.GenericExecuteSpec> {
        val hasTags = !tags.isNullOrEmpty()
        val tagJoin =
            if (hasTags) "JOIN post_tags ptags ON ptags.post_id = p.id JOIN tags t ON t.id = ptags.tag_id" else ""
        val conditions = mutableListOf("p.site_id = :siteId")

        if (status != null) conditions.add("p.status = :status")

        if (hasTags) conditions.add("t.name = ANY(:tags)")

        if (search != null) conditions.add("EXISTS (SELECT 1 FROM post_translations pts WHERE pts.post_id = p.id AND pts.title ILIKE :search)")

        val sql = "$select FROM posts p $tagJoin WHERE ${conditions.joinToString(" AND ")} $tail"
        val bind: (DatabaseClient.GenericExecuteSpec) -> DatabaseClient.GenericExecuteSpec =
            { spec ->
                var s = spec.bind("siteId", siteId)
                if (status != null) s = s.bind("status", status)
                if (hasTags) s = s.bind("tags", tags!!.toTypedArray())
                if (search != null) s = s.bind("search", "%$search%")
                s
            }
        return sql to bind
    }

    fun update(
        id: Long,
        siteId: Long,
        coverUrl: String?,
        status: PostStatus?,
        publishedAt: OffsetDateTime?,
        scheduledAt: OffsetDateTime?
    ): Mono<Post> =
        client.sql(
            """
            UPDATE posts SET
                cover_url    = COALESCE(:coverUrl, cover_url),
                status       = COALESCE(:status, status),
                published_at = COALESCE(:publishedAt, published_at),
                scheduled_at = COALESCE(:scheduledAt, scheduled_at),
                updated_at   = now()
            WHERE id = :id AND site_id = :siteId
            RETURNING *
        """
        )
            .bind("id", id)
            .bind("siteId", siteId)
            .bindNullable<String>("coverUrl", coverUrl)
            .bindNullable<String>("status", status?.name?.lowercase())
            .bindNullable<OffsetDateTime>("publishedAt", publishedAt)
            .bindNullable<OffsetDateTime>("scheduledAt", scheduledAt)
            .fetch().first()
            .map { mapToPost(it) }

    fun publishScheduled(): Mono<Int> =
        client.sql(
            """
            UPDATE posts SET status = 'published', published_at = now(), scheduled_at = null, updated_at = now()
            WHERE status = 'scheduled' AND scheduled_at <= now()
        """
        )
            .fetch().rowsUpdated()
            .map { it.toInt() }

    fun delete(id: Long, siteId: Long): Mono<Void> =
        client.sql("DELETE FROM posts WHERE id = :id AND site_id = :siteId")
            .bind("id", id)
            .bind("siteId", siteId)
            .then()

    fun createTranslationShell(
        postId: Long,
        siteId: Long,
        lang: String,
        title: String,
        slug: String,
        body: String,
        excerpt: String?
    ): Mono<PostTranslation> =
        client.sql(
            """
            INSERT INTO post_translations (post_id, site_id, lang, title, slug, body, excerpt)
            VALUES (:postId, :siteId, :lang, :title, :slug, :body, :excerpt)
            RETURNING *
        """
        )
            .bind("postId", postId)
            .bind("siteId", siteId)
            .bind("lang", lang)
            .bind("title", title)
            .bind("slug", slug)
            .bind("body", body)
            .bindNullable<String>("excerpt", excerpt)
            .fetch().first()
            .map { mapToTranslation(it) }

    fun findTranslationsByPostId(postId: Long): Flux<PostTranslation> =
        client.sql("SELECT * FROM post_translations WHERE post_id = :postId")
            .bind("postId", postId)
            .fetch().all()
            .map { mapToTranslation(it) }

    fun createVersion(
        postTranslationId: Long,
        title: String,
        slug: String,
        body: String,
        excerpt: String?,
        authorId: Long?
    ): Mono<PostTranslationVersion> =
        client.sql(
            """
            INSERT INTO post_translation_versions
                (post_translation_id, version_number, title, slug, body, excerpt, author_id)
            SELECT :postTranslationId,
                   COALESCE(MAX(version_number), 0) + 1,
                   :title, :slug, :body, :excerpt, :authorId
            FROM post_translation_versions WHERE post_translation_id = :postTranslationId
            RETURNING *
        """
        )
            .bind("postTranslationId", postTranslationId)
            .bind("title", title)
            .bind("slug", slug)
            .bind("body", body)
            .bindNullable<String>("excerpt", excerpt)
            .bindNullable<Long>("authorId", authorId)
            .fetch().first()
            .map { mapToVersion(it) }

    fun pruneOldDrafts(postTranslationId: Long, cap: Int = 30): Mono<Void> =
        client.sql(
            """
            DELETE FROM post_translation_versions
            WHERE id IN (
                SELECT id FROM post_translation_versions
                WHERE post_translation_id = :postTranslationId AND status = 'draft'
                ORDER BY version_number ASC
                LIMIT GREATEST(
                    0,
                    (SELECT COUNT(*) FROM post_translation_versions WHERE post_translation_id = :postTranslationId) - :cap
                )
            )
        """
        )
            .bind("postTranslationId", postTranslationId)
            .bind("cap", cap)
            .then()

    fun findVersionsByTranslationId(postTranslationId: Long): Flux<PostTranslationVersion> =
        client.sql(
            "SELECT * FROM post_translation_versions WHERE post_translation_id = :postTranslationId ORDER BY version_number DESC"
        )
            .bind("postTranslationId", postTranslationId)
            .fetch().all()
            .map { mapToVersion(it) }

    fun findVersionById(id: Long, postTranslationId: Long): Mono<PostTranslationVersion> =
        client.sql("SELECT * FROM post_translation_versions WHERE id = :id AND post_translation_id = :postTranslationId")
            .bind("id", id)
            .bind("postTranslationId", postTranslationId)
            .fetch().first()
            .map { mapToVersion(it) }

    fun findLatestVersionsByPostId(postId: Long): Flux<Pair<String, PostTranslationVersion>> =
        client.sql(
            """
            SELECT DISTINCT ON (pt.lang) pt.lang, ptv.*
            FROM post_translations pt
            JOIN post_translation_versions ptv ON ptv.post_translation_id = pt.id
            WHERE pt.post_id = :postId
            ORDER BY pt.lang, ptv.version_number DESC
        """
        )
            .bind("postId", postId)
            .fetch().all()
            .map { (it["lang"] as String) to mapToVersion(it) }

    fun publishVersion(versionId: Long, postTranslationId: Long): Mono<PostTranslation> =
        client.sql(
            """
            WITH v AS (
                UPDATE post_translation_versions
                SET status = 'published', published_at = COALESCE(published_at, now()), updated_at = now()
                WHERE id = :versionId AND post_translation_id = :postTranslationId
                RETURNING *
            )
            UPDATE post_translations pt
            SET title = v.title, slug = v.slug, body = v.body, excerpt = v.excerpt,
                current_version_id = v.id, updated_at = now()
            FROM v WHERE pt.id = v.post_translation_id
            RETURNING pt.*
        """
        )
            .bind("versionId", versionId)
            .bind("postTranslationId", postTranslationId)
            .fetch().first()
            .map { mapToTranslation(it) }

    /**
     * Publishes the latest draft of every translation on this post that has never gone live —
     * lets the post-level "Publish" action put a brand-new post's first drafts online in one
     * click, without touching translations that already have a deliberately-chosen live version.
     */
    fun publishInitialVersions(postId: Long): Mono<Void> =
        client.sql(
            """
            WITH latest AS (
                SELECT DISTINCT ON (pt.id) pt.id AS post_translation_id, ptv.id AS version_id,
                       ptv.title, ptv.slug, ptv.body, ptv.excerpt
                FROM post_translations pt
                JOIN post_translation_versions ptv ON ptv.post_translation_id = pt.id
                WHERE pt.post_id = :postId AND pt.current_version_id IS NULL
                ORDER BY pt.id, ptv.version_number DESC
            ),
            marked AS (
                UPDATE post_translation_versions v
                SET status = 'published', published_at = COALESCE(v.published_at, now()), updated_at = now()
                FROM latest l WHERE v.id = l.version_id
            )
            UPDATE post_translations pt
            SET title = l.title, slug = l.slug, body = l.body, excerpt = l.excerpt,
                current_version_id = l.version_id, updated_at = now()
            FROM latest l WHERE pt.id = l.post_translation_id
        """
        )
            .bind("postId", postId)
            .then()

    fun findTranslationSummariesByPostIds(postIds: List<Long>): Flux<PostTranslationSummary> {
        if (postIds.isEmpty()) return Flux.empty()
        val placeholders = postIds.indices.joinToString(",") { ":id$it" }
        var spec =
            client.sql("SELECT post_id, lang, slug, title FROM post_translations WHERE post_id IN ($placeholders)")
        postIds.forEachIndexed { i, id -> spec = spec.bind("id$i", id) }
        return spec.fetch().all().map {
            PostTranslationSummary(
                postId = it["post_id"] as Long,
                lang = it["lang"] as String,
                slug = it["slug"] as String,
                title = it["title"] as String
            )
        }
    }

    fun findPublishedBySiteAndLang(
        siteId: Long,
        lang: String,
        page: Int,
        size: Int,
        tag: String? = null,
        search: String? = null,
        sort: String? = null
    ): Flux<Pair<Post, PostTranslation>> {
        val direction = if (sort == "asc") "ASC" else "DESC"
        val (sql, bind) = buildBlogQuery(
            """
                SELECT p.id, p.site_id, p.status, p.cover_url, p.view_count,
                      p.published_at, p.scheduled_at, p.created_at, p.updated_at,
                      pt.id AS pt_id, pt.post_id AS pt_post_id, pt.site_id AS pt_site_id,
                      pt.lang AS pt_lang, pt.title AS pt_title, pt.slug AS pt_slug,
                      pt.body AS pt_body, pt.excerpt AS pt_excerpt, pt.current_version_id AS pt_current_version_id,
                      pt.created_at AS pt_created_at, pt.updated_at AS pt_updated_at
                      """,
            "ORDER BY p.published_at $direction LIMIT :limit OFFSET :offset", siteId, lang, tag, search
        )
        return bind(client.sql(sql))
            .bind("limit", size).bind("offset", page * size)
            .fetch().all().map { mapToPostAndTranslation(it) }
    }

    fun countPublishedBySiteAndLang(
        siteId: Long,
        lang: String,
        tag: String? = null,
        search: String? = null
    ): Mono<Long> {
        val (sql, bind) = buildBlogQuery("SELECT COUNT(DISTINCT p.id)", "", siteId, lang, tag, search)
        return bind(client.sql(sql)).fetch().first().map { it["count"] as Long }
    }

    private fun buildBlogQuery(
        select: String,
        tail: String,
        siteId: Long,
        lang: String,
        tag: String?,
        search: String?
    ): Pair<String, (org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec) -> org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec> {
        val tagJoin =
            if (tag != null) "JOIN post_tags ptags ON ptags.post_id = p.id JOIN tags t ON t.id = ptags.tag_id" else ""
        val conditions = mutableListOf("p.site_id = :siteId", "p.status = 'published'", "pt.current_version_id IS NOT NULL")
        if (tag != null) conditions.add("t.name = :tag")
        if (search != null) conditions.add("(pt.title ILIKE :search OR pt.excerpt ILIKE :search)")
        val sql =
            "$select FROM posts p JOIN post_translations pt ON pt.post_id = p.id AND pt.lang = :lang AND pt.site_id = :siteId $tagJoin WHERE ${
                conditions.joinToString(" AND ")
            } $tail"
        val bind: (org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec) -> org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec =
            { spec ->
                var s = spec.bind("siteId", siteId).bind("lang", lang)
                if (tag != null) s = s.bind("tag", tag)
                if (search != null) s = s.bind("search", "%$search%")
                s
            }
        return sql to bind
    }

    fun findPublishedBySlug(
        siteId: Long,
        lang: String,
        slug: String,
        user: Long? = -1
    ): Mono<Pair<Post, PostTranslation>> =
        client.sql(
            """
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
                pt.current_version_id AS pt_current_version_id,
                pt.created_at AS pt_created_at,
                pt.updated_at AS pt_updated_at
            FROM posts p
            JOIN post_translations pt ON pt.post_id = p.id AND pt.lang = :lang AND pt.site_id = :siteId
            JOIN sites s ON s.id = p.site_id
            WHERE p.site_id = :siteId AND (p.status = 'published' OR s.user_id = :user)
                AND pt.slug = :slug AND (pt.current_version_id IS NOT NULL OR s.user_id = :user)
        """
        )
            .bind("siteId", siteId)
            .bind("lang", lang)
            .bind("slug", slug)
            .bind("user", user ?: -1)
            .fetch().first()
            .map { mapToPostAndTranslation(it) }

    fun findAllPublishedForSitemap(siteId: Long): Flux<SitemapEntry> =
        client.sql(
            """
            SELECT pt.lang, pt.slug, GREATEST(p.updated_at, pt.updated_at) AS last_mod
            FROM posts p
            JOIN post_translations pt ON pt.post_id = p.id AND pt.site_id = :siteId
            WHERE p.site_id = :siteId AND p.status = 'published' AND pt.current_version_id IS NOT NULL
            ORDER BY p.published_at DESC
        """
        )
            .bind("siteId", siteId)
            .fetch().all()
            .map {
                SitemapEntry(
                    lang = it["lang"] as String,
                    slug = it["slug"] as String,
                    lastMod = it["last_mod"] as OffsetDateTime
                )
            }

    fun incrementViewCount(postId: Long): Mono<Void> =
        client.sql("UPDATE posts SET view_count = view_count + 1 WHERE id = :id")
            .bind("id", postId)
            .then()

    fun findTopByViews(limit: Int): Flux<PostViewStat> =
        client.sql(
            """
            SELECT p.id, p.view_count, p.site_id,
                   s.name AS site_name, s.domain,
                   pt.title, pt.lang, pt.slug
            FROM posts p
            JOIN sites s ON s.id = p.site_id
            LEFT JOIN LATERAL (
                SELECT title, lang, slug FROM post_translations
                WHERE post_id = p.id ORDER BY lang LIMIT 1
            ) pt ON true
            WHERE p.status = 'published'
            ORDER BY p.view_count DESC
            LIMIT :limit
            """
        )
            .bind("limit", limit)
            .fetch().all()
            .map {
                PostViewStat(
                    postId = it["id"] as Long,
                    viewCount = it["view_count"] as Long,
                    siteId = it["site_id"] as Long,
                    siteName = it["site_name"] as String,
                    domain = it["domain"] as String,
                    title = it["title"] as? String ?: "(untitled)",
                    lang = it["lang"] as? String ?: "",
                    slug = it["slug"] as? String ?: ""
                )
            }

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
        currentVersionId = row["current_version_id"] as? Long,
        createdAt = row["created_at"] as OffsetDateTime,
        updatedAt = row["updated_at"] as OffsetDateTime
    )

    private fun mapToPostAndTranslation(row: Map<String, Any>): Pair<Post, PostTranslation> =
        mapToPost(row) to PostTranslation(
            id = row["pt_id"] as Long,
            postId = row["pt_post_id"] as Long,
            siteId = row["pt_site_id"] as Long,
            lang = row["pt_lang"] as String,
            title = row["pt_title"] as String,
            slug = row["pt_slug"] as String,
            body = row["pt_body"] as String,
            excerpt = row["pt_excerpt"] as? String,
            currentVersionId = row["pt_current_version_id"] as? Long,
            createdAt = row["pt_created_at"] as OffsetDateTime,
            updatedAt = row["pt_updated_at"] as OffsetDateTime
        )

    private fun mapToVersion(row: Map<String, Any>): PostTranslationVersion = PostTranslationVersion(
        id = row["id"] as Long,
        postTranslationId = row["post_translation_id"] as Long,
        versionNumber = row["version_number"] as Int,
        status = VersionStatus.valueOf((row["status"] as String).uppercase()),
        title = row["title"] as String,
        slug = row["slug"] as String,
        body = row["body"] as String,
        excerpt = row["excerpt"] as? String,
        authorId = row["author_id"] as? Long,
        createdAt = row["created_at"] as OffsetDateTime,
        publishedAt = row["published_at"] as? OffsetDateTime,
        updatedAt = row["updated_at"] as OffsetDateTime
    )
}
