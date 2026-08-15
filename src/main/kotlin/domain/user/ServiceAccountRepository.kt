package com.gonzalinux.domain.user

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class ServiceAccountRepository(private val client: DatabaseClient) {

    /**
     * `email` only needs to satisfy the column's NOT NULL UNIQUE constraint here — [name] is the
     * identity a human reads, stored separately as `display_name`.
     */
    fun create(ownerId: Long, name: String, tokenHash: String): Mono<User> =
        client.sql(
            """
            INSERT INTO users (email, display_name, email_verified, owner_id, service_account_token_hash)
            VALUES (:email, :name, true, :ownerId, :tokenHash)
            RETURNING *
            """
        )
            .bind("email", "sa-${UUID.randomUUID()}@service.internal")
            .bind("name", name)
            .bind("ownerId", ownerId)
            .bind("tokenHash", tokenHash)
            .fetch().first()
            .map { User.fromData(it) }

    fun findAllByOwnerId(ownerId: Long): Flux<User> =
        client.sql("SELECT * FROM users WHERE owner_id = :ownerId ORDER BY created_at DESC")
            .bind("ownerId", ownerId)
            .fetch().all()
            .map { User.fromData(it) }

    /** Backs the invite-time ownership constraint in `Collaboration.md` — checked in the service layer, not the DB. */
    fun existsByIdAndOwnerId(id: Long, ownerId: Long): Mono<Boolean> =
        client.sql("SELECT 1 FROM users WHERE id = :id AND owner_id = :ownerId LIMIT 1")
            .bind("id", id)
            .bind("ownerId", ownerId)
            .fetch().first()
            .map { true }
            .defaultIfEmpty(false)

    /** Scoped by owner so one user cannot revoke another's service account by guessing ids. */
    fun deleteByIdAndOwnerId(id: Long, ownerId: Long): Mono<Long> =
        client.sql("DELETE FROM users WHERE id = :id AND owner_id = :ownerId")
            .bind("id", id)
            .bind("ownerId", ownerId)
            .fetch().rowsUpdated()

    fun updateTokenHash(id: Long, ownerId: Long, tokenHash: String): Mono<Long> =
        client.sql("UPDATE users SET service_account_token_hash = :tokenHash WHERE id = :id AND owner_id = :ownerId")
            .bind("tokenHash", tokenHash)
            .bind("id", id)
            .bind("ownerId", ownerId)
            .fetch().rowsUpdated()
}
