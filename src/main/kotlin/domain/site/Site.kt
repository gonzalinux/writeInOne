package com.gonzalinux.domain.site

import com.gonzalinux.domain.Languages
import java.time.OffsetDateTime

data class Site(
    val id: Long,
    val userId: Long,
    val name: String,
    val domain: String,
    val prefix: String,
    val description: String?,
    val stylesUrl: String?,
    val customCss: String? = null,
    val availableThemes: List<Theme>,
    val languages: List<Languages>,
    val config: SiteConfig,
    val status: SiteStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val verifyDate: OffsetDateTime,
    val role: Roles? = null,

    )

enum class SiteStatus {
    NOT_VERIFIED,
    VERIFIED;
}

/** A subdomain label parked for its previous owner after a rename or a site deletion. */
data class SubdomainReservation(
    val label: String,
    val userId: Long,
    val releasedAt: OffsetDateTime
)