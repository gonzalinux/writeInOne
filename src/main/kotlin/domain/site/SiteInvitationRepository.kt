package com.gonzalinux.domain.site

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

@Repository
class SiteInvitationRepository(private val client: DatabaseClient) {

    fun create(siteId: Long, role: Roles, tokenHash: String, expiresAt: OffsetDateTime): Mono<SiteInvitation> =
        client.sql(
            """
            INSERT INTO site_invitations (site_id, role, token_hash, expires_at)
            VALUES (:siteId, :role, :tokenHash, :expiresAt)
            RETURNING *
            """
        )
            .bind("siteId", siteId)
            .bind("role", role.name)
            .bind("tokenHash", tokenHash)
            .bind("expiresAt", expiresAt)
            .fetch().first()
            .map { mapToInvitation(it) }

    /** Live invitations only — an expired one is no longer something an admin can act on. */
    fun findActiveBySiteId(siteId: Long): Flux<SiteInvitation> =
        client.sql(
            """
            SELECT * FROM site_invitations
            WHERE site_id = :siteId AND expires_at > now()
            ORDER BY created_at DESC
            """
        )
            .bind("siteId", siteId)
            .fetch().all()
            .map { mapToInvitation(it) }

    fun findByTokenHash(tokenHash: String): Mono<SiteInvitation> =
        client.sql("SELECT * FROM site_invitations WHERE token_hash = :tokenHash")
            .bind("tokenHash", tokenHash)
            .fetch().first()
            .map { mapToInvitation(it) }

    /** Scoped by site so an admin of one site cannot revoke another site's invitation by guessing ids. */
    fun delete(siteId: Long, id: Long): Mono<Long> =
        client.sql("DELETE FROM site_invitations WHERE id = :id AND site_id = :siteId")
            .bind("id", id)
            .bind("siteId", siteId)
            .fetch().rowsUpdated()

    /**
     * Grants the membership and stamps the redemption in one statement, so an invitation that
     * expires between the caller's read and this write cannot still let someone in.
     * `ON CONFLICT DO NOTHING` keeps an existing member's role intact — a stale writer invite
     * must never demote an admin. Returns false when nothing was granted.
     */
    fun redeem(id: Long, userId: Long): Mono<Boolean> {
        return client.sql(
            """
            WITH claim AS (
                UPDATE site_invitations SET accepted_at = now()
                WHERE id = :id AND expires_at > now()
                RETURNING site_id, role
            )
            INSERT INTO site_members (site_id, user_id, role)
            SELECT site_id, :userId, role FROM claim
            ON CONFLICT (user_id, site_id) DO NOTHING
            RETURNING user_id
            """
        )
            .bind("id", id)
            .bind("userId", userId)
            .fetch().first()
            .map { true }
            .defaultIfEmpty(false)
    }

    private fun mapToInvitation(row: Map<String, Any>): SiteInvitation =
        SiteInvitation(
            id = row["id"] as Long,
            siteId = row["site_id"] as Long,
            role = Roles.from(row["role"] as String?) ?: Roles.WRITER,
            createdAt = row["created_at"] as OffsetDateTime,
            expiresAt = row["expires_at"] as OffsetDateTime,
            acceptedAt = row["accepted_at"] as? OffsetDateTime
        )
}
