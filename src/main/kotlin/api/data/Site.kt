package com.gonzalinux.api.data

import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.site.SiteConfig
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreateSiteRequest(
    @field:NotBlank val name: String,
    @field:NotBlank @field:Pattern(
        regexp = "^[a-z0-9][a-z0-9.-]+[a-z0-9]$",
        message = "must be a valid domain (e.g. blog.gonzalo.com)"
    ) val domain: String,
    val description: String? = null,
    val stylesUrl: String? = null,
    val languages: List<Languages> = listOf(Languages.ENGLISH),
    val config: SiteConfig = SiteConfig()
)

data class UpdateSiteRequest(
    val name: String? = null,
    val description: String? = null,
    val stylesUrl: String? = null,
    val languages: List<Languages>? = null,
    val config: SiteConfig? = null
)
