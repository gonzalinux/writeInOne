package com.gonzalinux.blogs

import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.post.Post
import com.gonzalinux.domain.post.PostTranslation
import com.gonzalinux.domain.tag.Tag

data class BlogPostSummary(
    val post: Post,
    val translation: PostTranslation,
    val tags: List<Tag>
)

data class BlogPostDetail(
    val post: Post,
    val translation: PostTranslation,
    val tags: List<Tag>,
    val renderedBody: String,
    val allTranslations: List<PostTranslation>
)
