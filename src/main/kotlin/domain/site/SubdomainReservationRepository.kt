package com.gonzalinux.domain.site

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class SubdomainReservationRepository(private val client: DatabaseClient) {

    /** Only reservations still inside the window count — expiry must not depend on the purge job. */
    fun findActive(label: String, days: Long): Mono<SubdomainReservation> =
        client.sql("SELECT * FROM subdomain_reservations WHERE label = :label AND released_at > :cutoff")
            .bind("label", label)
            .bind("cutoff", OffsetDateTime.now().minusDays(days))
            .fetch().first()
            .map {
                SubdomainReservation(
                    label = it["label"] as String,
                    userId = it["user_id"] as Long,
                    releasedAt = it["released_at"] as OffsetDateTime
                )
            }

    fun reserve(label: String, userId: Long): Mono<Void> =
        client.sql("""
            INSERT INTO subdomain_reservations (label, user_id, released_at)
            VALUES (:label, :userId, now())
            ON CONFLICT (label) DO UPDATE SET user_id = :userId, released_at = now()
        """)
            .bind("label", label)
            .bind("userId", userId)
            .then()

    fun release(label: String): Mono<Void> =
        client.sql("DELETE FROM subdomain_reservations WHERE label = :label")
            .bind("label", label)
            .then()

    fun deleteExpired(days: Long): Mono<Long> =
        client.sql("DELETE FROM subdomain_reservations WHERE released_at <= :cutoff")
            .bind("cutoff", OffsetDateTime.now().minusDays(days))
            .fetch().rowsUpdated()
}
