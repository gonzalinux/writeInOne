package com.gonzalinux.domain.post

import com.gonzalinux.domain.tag.Tag
import java.time.OffsetDateTime

enum class PostStatus { DRAFT, SCHEDULED, PUBLISHED, ARCHIVED }

enum class VersionStatus { DRAFT, PUBLISHED }

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
    val currentVersionId: Long?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

data class PostTranslationVersion(
    val id: Long,
    val postTranslationId: Long,
    val versionNumber: Int,
    val status: VersionStatus,
    val title: String,
    val slug: String,
    val body: String,
    val excerpt: String?,
    val authorId: Long?,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime
)

data class PostWithTranslations(
    val post: Post,
    val translations: List<PostTranslation>,
    val tags: List<Tag> = emptyList(),
    val latestVersions: Map<String, PostTranslationVersion> = emptyMap()
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

data class PostViewStat(
    val postId: Long,
    val viewCount: Long,
    val siteId: Long,
    val siteName: String,
    val domain: String,
    val title: String,
    val lang: String,
    val slug: String
)

