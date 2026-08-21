package com.gonzalinux.domain.tag

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class TagRepository(private val client: DatabaseClient) {

    /**
     * The unique constraint on (site_id, name) is case-sensitive, so an exact ON CONFLICT upsert
     * alone would let "mcp-test" and "MCP-Test" pile up as separate tags. Checking case-insensitively
     * first and reusing whatever casing already exists in the DB keeps a site's tag list from
     * fragmenting just because a caller typed a name slightly differently.
     */
    fun findOrCreate(siteId: Long, name: String): Mono<Tag> =
        client.sql("SELECT * FROM tags WHERE site_id = :siteId AND LOWER(name) = LOWER(:name)")
            .bind("siteId", siteId)
            .bind("name", name)
            .fetch().first()
            .map { mapToTag(it) }
            .switchIfEmpty(
                client.sql(
                    """
                    INSERT INTO tags (site_id, name) VALUES (:siteId, :name)
                    ON CONFLICT (site_id, name) DO UPDATE SET name = EXCLUDED.name
                    RETURNING *
                """
                )
                    .bind("siteId", siteId)
                    .bind("name", name)
                    .fetch().first()
                    .map { mapToTag(it) }
            )

    fun findBySiteId(siteId: Long, search: String? = null, limit: Int? = null): Flux<Tag> {
        val conditions = mutableListOf("site_id = :siteId")
        if (search != null) conditions.add("name ILIKE :search")
        val limitClause = if (limit != null) "LIMIT :limit" else ""
        val sql = "SELECT * FROM tags WHERE ${conditions.joinToString(" AND ")} ORDER BY name $limitClause"

        var spec = client.sql(sql).bind("siteId", siteId)
        if (search != null) spec = spec.bind("search", "%$search%")
        if (limit != null) spec = spec.bind("limit", limit)
        return spec.fetch().all().map { mapToTag(it) }
    }

    fun findByPostId(postId: Long): Flux<Tag> =
        client.sql(
            """
            SELECT t.* FROM tags t
            JOIN post_tags pt ON t.id = pt.tag_id
            WHERE pt.post_id = :postId
            ORDER BY t.name
        """
        )
            .bind("postId", postId)
            .fetch().all()
            .map { mapToTag(it) }

    fun assignToPost(postId: Long, tagId: Long): Mono<Void> =
        client.sql("INSERT INTO post_tags (post_id, tag_id) VALUES (:postId, :tagId) ON CONFLICT DO NOTHING")
            .bind("postId", postId)
            .bind("tagId", tagId)
            .then()

    fun replacePostTags(postId: Long, tagIds: List<Long>): Mono<Void> {
        val deleteOld = client.sql("DELETE FROM post_tags WHERE post_id = :postId")
            .bind("postId", postId)
            .then()
        if (tagIds.isEmpty()) return deleteOld
        return deleteOld.thenMany(
            Flux.merge(tagIds.map { tagId ->
                client.sql("INSERT INTO post_tags (post_id, tag_id) VALUES (:postId, :tagId)")
                    .bind("postId", postId)
                    .bind("tagId", tagId)
                    .then()
            })
        ).then()
    }

    fun delete(id: Long, siteId: Long): Mono<Void> =
        client.sql("DELETE FROM tags WHERE id = :id AND site_id = :siteId")
            .bind("id", id)
            .bind("siteId", siteId)
            .then()

    fun findByPostIds(postIds: List<Long>): Flux<Pair<Long, Tag>> {
        if (postIds.isEmpty()) return Flux.empty()
        val placeholders = postIds.indices.joinToString(",") { ":id$it" }
        var spec = client.sql(
            """
            SELECT pt.post_id, t.* FROM tags t
            JOIN post_tags pt ON t.id = pt.tag_id
            WHERE pt.post_id IN ($placeholders)
            ORDER BY t.name
        """
        )
        postIds.forEachIndexed { i, id -> spec = spec.bind("id$i", id) }
        return spec.fetch().all().map { row -> (row["post_id"] as Long) to mapToTag(row) }
    }

    private fun mapToTag(row: Map<String, Any>): Tag = Tag(
        id = row["id"] as Long,
        siteId = row["site_id"] as Long,
        name = row["name"] as String,
        createdAt = row["created_at"] as OffsetDateTime
    )
}
