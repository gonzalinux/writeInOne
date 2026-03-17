package com.gonzalinux.domain.tag

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class TagRepository(private val client: DatabaseClient) {

    fun findOrCreate(siteId: Long, name: String): Mono<Tag> =
        client.sql("""
            INSERT INTO tags (site_id, name) VALUES (:siteId, :name)
            ON CONFLICT (site_id, name) DO UPDATE SET name = EXCLUDED.name
            RETURNING *
        """)
            .bind("siteId", siteId)
            .bind("name", name)
            .fetch().first()
            .map { mapToTag(it) }

    fun findBySiteId(siteId: Long): Flux<Tag> =
        client.sql("SELECT * FROM tags WHERE site_id = :siteId ORDER BY name")
            .bind("siteId", siteId)
            .fetch().all()
            .map { mapToTag(it) }

    fun findByPostId(postId: Long): Flux<Tag> =
        client.sql("""
            SELECT t.* FROM tags t
            JOIN post_tags pt ON t.id = pt.tag_id
            WHERE pt.post_id = :postId
            ORDER BY t.name
        """)
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

    private fun mapToTag(row: Map<String, Any>): Tag = Tag(
        id = row["id"] as Long,
        siteId = row["site_id"] as Long,
        name = row["name"] as String,
        createdAt = row["created_at"] as OffsetDateTime
    )
}
