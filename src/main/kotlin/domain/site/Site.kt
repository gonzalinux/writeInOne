package com.gonzalinux.domain.site

import com.gonzalinux.domain.Languages
import java.time.OffsetDateTime

data class Site(
    val id: Long,
    val userId: Long,
    val name: String,
    val domain: String,
    val description: String?,
    val stylesUrl: String?,
    val availableThemes: List<Theme>,
    val languages: List<Languages>,
    val config: SiteConfig,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)