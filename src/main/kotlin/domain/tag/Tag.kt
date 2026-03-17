package com.gonzalinux.domain.tag

import java.time.OffsetDateTime

data class Tag(
    val id: Long,
    val siteId: Long,
    val name: String,
    val createdAt: OffsetDateTime
)
