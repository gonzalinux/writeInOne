package com.gonzalinux.domain.post

import java.time.LocalDateTime

enum class PostStatus { DRAFT, PUBLISHED, ARCHIVED }

data class Post(
    val id: Long,
    val siteId: Long,
    val status: PostStatus,
    val coverUrl: String?,
    val viewCount: Long,
    val publishedAt: LocalDateTime?,
    val scheduledAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class PostTranslation(
    val id: Long,
    val postId: Long,
    val siteId: Long,
    val lang: String,
    val title: String,
    val slug: String,
    val body: String,
    val excerpt: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
