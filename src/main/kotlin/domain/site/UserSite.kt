package com.gonzalinux.domain.site

import java.time.OffsetDateTime

data class UserSite(
    val siteId: Long,
    val role: Roles,
    val userId: Long,
    val displayName: String,
    val createdAt: OffsetDateTime,
    val email: String,
)