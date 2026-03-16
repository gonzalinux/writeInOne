package com.gonzalinux.api.data

import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

data class TranslationInput(
    @field:NotBlank val title: String,
    @field:NotBlank val body: String,
    val slug: String? = null,
    val excerpt: String? = null
)

data class CreatePostRequest(
    val coverUrl: String? = null,
    val translations: Map<String, TranslationInput> = emptyMap()
)

data class UpdatePostRequest(
    val coverUrl: String? = null,
    val translations: Map<String, TranslationInput>? = null
)

data class SchedulePostRequest(
    val scheduledAt: OffsetDateTime
)
