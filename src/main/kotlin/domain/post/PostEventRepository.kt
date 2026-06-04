package com.gonzalinux.domain.post

import com.gonzalinux.common.bindNullable
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class PostEventRepository(private val client: DatabaseClient) {

    fun fingerprintExistsInWindow(hash: String, windowHours: Int): Mono<Boolean> =
        client.sql(
            "SELECT COUNT(1) FROM post_events WHERE fingerprint_hash = :hash AND created_at > now() - :window * interval '1 hour'"
        )
            .bind("hash", hash)
            .bind("window", windowHours)
            .fetch().first()
            .map { (it["count"] as Long) > 0 }

    fun record(postId: Long, eventType: String, value: Int, hash: String?): Mono<Void> =
        client.sql(
            "INSERT INTO post_events (post_id, event_type, value, fingerprint_hash) VALUES (:postId, :eventType, :value, :hash)"
        )
            .bind("postId", postId)
            .bind("eventType", eventType)
            .bind("value", value)
            .bindNullable<String>("hash", hash)
            .then()
}
