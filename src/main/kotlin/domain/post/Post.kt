package com.gonzalinux.domain.post

import com.gonzalinux.domain.tag.Tag
import java.time.OffsetDateTime

enum class PostStatus { DRAFT, SCHEDULED, PUBLISHED, ARCHIVED }

data class Post(
    val id: Long,
    val siteId: Long,
    val status: PostStatus,
    val coverUrl: String?,
    val viewCount: Long,
    val publishedAt: OffsetDateTime?,
    val scheduledAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
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
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class PostWithTranslations(
    val post: Post,
    val translations: List<PostTranslation>,
    val tags: List<Tag> = emptyList()
)

data class PostTranslationSummary(
    val postId: Long,
    val lang: String,
    val slug: String,
    val title: String
)

data class PostSummary(
    val post: Post,
    val translations: List<PostTranslationSummary>,
    val tags: List<Tag>
)

data class SitemapEntry(
    val lang: String,
    val slug: String,
    val lastMod: OffsetDateTime
)

