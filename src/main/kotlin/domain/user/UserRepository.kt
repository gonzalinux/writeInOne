package com.gonzalinux.domain.user

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class UserRepository(private val client: DatabaseClient) {

    fun findByUserId(userId: Long): Mono<User> =
        client.sql("SELECT * FROM users WHERE id = :id LIMIT 1")
            .bind("id", userId)
            .fetch().first()
            .map { mapToUser(it) }

    fun findByEmail(email: String): Mono<User> =
        client.sql("SELECT * FROM users WHERE email = :email LIMIT 1")
            .bind("email", email.lowercase())
            .fetch().first()
            .map { mapToUser(it) }

    fun findVerifiedByEmail(email: String): Mono<User> =
        client.sql("SELECT * FROM users WHERE email = :email AND email_verified = true LIMIT 1")
            .bind("email", email.lowercase())
            .fetch().first()
            .map { mapToUser(it) }

    fun deleteById(userId: Long): Mono<Void> =
        client.sql("DELETE FROM users WHERE id = :userId")
            .bind("userId", userId)
            .then()

    fun create(email: String, displayName: String, passwordHash: String): Mono<User> =
        client.sql("INSERT INTO users (email, display_name, password) VALUES (:email, :displayName, :passwordHash) RETURNING *")
            .bind("email", email.lowercase())
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
        client.sql("DELETE FROM refresh_tokens WHERE ctid IN (SELECT ctid FROM refresh_tokens WHERE expires_at < now() LIMIT :limit)")
            .bind("limit", limit)
            .then()

    fun updateEmailVerified(userId: Long): Mono<Void> =
        client.sql("UPDATE users SET email_verified = true WHERE id = :userId")
            .bind("userId", userId)
            .then()

    fun updatePassword(userId: Long, passwordHash: String): Mono<Void> =
        client.sql("UPDATE users SET password = :passwordHash WHERE id = :userId")
            .bind("userId", userId)
            .bind("passwordHash", passwordHash)
            .then()

    fun saveEmailVerificationToken(userId: Long, tokenHash: String, expiresAt: OffsetDateTime): Mono<Void> =
        client.sql("INSERT INTO email_verification_tokens (token_hash, user_id, expires_at) VALUES (:tokenHash, :userId, :expiresAt) ON CONFLICT (token_hash) DO NOTHING")
            .bind("tokenHash", tokenHash)
            .bind("userId", userId)
            .bind("expiresAt", expiresAt)
            .then()

    fun findEmailVerificationToken(userId: Long, tokenHash: String): Mono<StoredEmailToken> =
        client.sql("SELECT * FROM email_verification_tokens WHERE user_id = :userId AND token_hash = :tokenHash AND expires_at > now() LIMIT 1")
            .bind("userId", userId)
            .bind("tokenHash", tokenHash)
            .fetch().first()
            .map { row ->
                StoredEmailToken(
                    userId = row["user_id"] as Long,
                    tokenHash = row["token_hash"] as String,
                    expiresAt = row["expires_at"] as OffsetDateTime
                )
            }

    fun deleteEmailVerificationTokensByUserId(userId: Long): Mono<Void> =
        client.sql("DELETE FROM email_verification_tokens WHERE user_id = :userId")
            .bind("userId", userId)
            .then()

    fun deleteEmailVerificationToken(tokenHash: String): Mono<Void> =
        client.sql("DELETE FROM email_verification_tokens WHERE token_hash = :tokenHash")
            .bind("tokenHash", tokenHash)
            .then()

    fun deleteExpiredEmailVerificationTokens(limit: Int): Mono<Void> =
        client.sql("DELETE FROM email_verification_tokens WHERE ctid IN (SELECT ctid FROM email_verification_tokens WHERE expires_at < now() LIMIT :limit)")
            .bind("limit", limit)
            .then()

    fun deleteUnverifiedExpiredUsers(): Mono<Void> =
        client.sql("""
            DELETE FROM users
            WHERE email_verified = false
            AND NOT EXISTS (
                SELECT 1 FROM email_verification_tokens
                WHERE user_id = users.id AND expires_at > now()
            )
        """.trimIndent())
            .then()

    fun savePasswordResetToken(userId: Long, tokenHash: String, expiresAt: OffsetDateTime): Mono<Void> =
        client.sql("INSERT INTO password_reset_tokens (token_hash, user_id, expires_at) VALUES (:tokenHash, :userId, :expiresAt) ON CONFLICT (token_hash) DO NOTHING")
            .bind("tokenHash", tokenHash)
            .bind("userId", userId)
            .bind("expiresAt", expiresAt)
            .then()

    fun findPasswordResetToken(userId: Long, tokenHash: String): Mono<StoredEmailToken> =
        client.sql("SELECT * FROM password_reset_tokens WHERE user_id = :userId AND token_hash = :tokenHash AND expires_at > now() LIMIT 1")
            .bind("userId", userId)
            .bind("tokenHash", tokenHash)
            .fetch().first()
            .map { row ->
                StoredEmailToken(
                    userId = row["user_id"] as Long,
                    tokenHash = row["token_hash"] as String,
                    expiresAt = row["expires_at"] as OffsetDateTime
                )
            }

    fun deletePasswordResetTokensByUserId(userId: Long): Mono<Void> =
        client.sql("DELETE FROM password_reset_tokens WHERE user_id = :userId")
            .bind("userId", userId)
            .then()

    fun deletePasswordResetToken(tokenHash: String): Mono<Void> =
        client.sql("DELETE FROM password_reset_tokens WHERE token_hash = :tokenHash")
            .bind("tokenHash", tokenHash)
            .then()

    fun deleteExpiredPasswordResetTokens(limit: Int): Mono<Void> =
        client.sql("DELETE FROM password_reset_tokens WHERE ctid IN (SELECT ctid FROM password_reset_tokens WHERE expires_at < now() LIMIT :limit)")
            .bind("limit", limit)
            .then()

    fun findRecent(limit: Int): Flux<User> =
        client.sql("SELECT * FROM users ORDER BY created_at DESC LIMIT :limit")
            .bind("limit", limit)
            .fetch().all()
            .map { mapToUser(it) }

    fun countAll(): Mono<Long> =
        client.sql("SELECT COUNT(*) as count FROM users")
            .fetch().first()
            .map { it["count"] as Long }

    private fun mapToUser(row: Map<String, Any>): User =
        User(
            id = row["id"] as Long,
            email = row["email"] as String,
            displayName = row["display_name"] as String,
            passwordHash = row["password"] as String,
            emailVerified = row["email_verified"] as Boolean,
            createdAt = row["created_at"] as OffsetDateTime,
            updatedAt = row["updated_at"] as OffsetDateTime
        )
}
