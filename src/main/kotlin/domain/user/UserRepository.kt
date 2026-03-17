package com.gonzalinux.domain.user

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class UserRepository(private val client: DatabaseClient) {

    fun findByUserId(userId: String): Mono<User> =
        client.sql("SELECT * FROM users WHERE id = :id LIMIT 1")
            .bind("id", userId)
            .fetch().first()
            .map { mapToUser(it) }

    fun findByEmail(email: String): Mono<User> =
        client.sql("SELECT * FROM users WHERE email = :email LIMIT 1")
            .bind("email", email)
            .fetch().first()
            .map { mapToUser(it) }

    fun create(email: String, displayName: String, passwordHash: String): Mono<User> =
        client.sql("INSERT INTO users (email, display_name, password) VALUES (:email, :displayName, :passwordHash) RETURNING *")
            .bind("email", email)
            .bind("displayName", displayName)
            .bind("passwordHash", passwordHash)
            .fetch().first()
            .map { mapToUser(it) }

    fun saveRefreshToken(userId: Long, tokenHash: String, expiresAt: OffsetDateTime): Mono<Void> =
        client.sql("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (:userId, :tokenHash, :expiresAt)")
            .bind("userId", userId)
            .bind("tokenHash", tokenHash)
            .bind("expiresAt", expiresAt)
            .then()

    fun findRefreshToken(tokenHash: String): Mono<StoredRefreshToken> =
        client.sql("SELECT * FROM refresh_tokens WHERE token_hash = :tokenHash AND expires_at > now() LIMIT 1")
            .bind("tokenHash", tokenHash)
            .fetch().first()
            .map { row ->
                StoredRefreshToken(
                    userId = row["user_id"] as Long,
                    tokenHash = row["token_hash"] as String,
                    expiresAt = row["expires_at"] as OffsetDateTime
                )
            }

    fun deleteRefreshToken(tokenHash: String): Mono<Void> =
        client.sql("DELETE FROM refresh_tokens WHERE token_hash = :tokenHash")
            .bind("tokenHash", tokenHash)
            .then()

    fun deleteExpiredTokens(limit: Int): Mono<Void> =
        client.sql("DELETE FROM refresh_tokens WHERE expires_at < now() LIMIT :limit")
            .bind("limit", limit)
            .then()

    private fun mapToUser(row: Map<String, Any>): User =
        User(
            id = row["id"] as Long,
            email = row["email"] as String,
            displayName = row["display_name"] as String,
            passwordHash = row["password"] as String,
            createdAt = row["created_at"] as OffsetDateTime,
            updatedAt = row["updated_at"] as OffsetDateTime
        )
}
