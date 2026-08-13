package com.gonzalinux.domain.site

import java.time.OffsetDateTime

/**
 * A shareable link granting [role] on a site. Anyone holding the token can redeem it until
 * [expiresAt], so this is not a pending membership — one invitation can bring in several people.
 *
 * The raw token never lives here: only its SHA-256 hash is stored, and this type is safe to
 * serialise straight to the API.
 */
data class SiteInvitation(
    val id: Long,
    val siteId: Long,
    val role: Roles,
    val createdAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    /** When the invitation was last redeemed, or null while nobody has used it yet. */
    val acceptedAt: OffsetDateTime?
)

/** The single moment the raw token exists outside the client's hands — after this only its hash is kept. */
data class CreatedInvitation(
    val invitation: SiteInvitation,
    val token: String,
    val acceptUrl: String
)
