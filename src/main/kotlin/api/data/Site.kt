package com.gonzalinux.api.data

import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.site.SiteConfig
import com.gonzalinux.domain.site.Theme
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class CreateSiteRequest(
    @field:NotBlank val name: String,
    @field:NotBlank @field:Pattern(
        regexp = "^[a-z0-9][a-z0-9.-]+[a-z0-9]$",
        message = "must be a valid domain (e.g. blog.site.com)"
    ) val domain: String,
    val description: String? = null,
    val stylesUrl: String? = null,
    val availableThemes: List<Theme> = listOf(Theme.LIGHT),
    val languages: List<Languages> = listOf(Languages.ENGLISH),
    val config: SiteConfig = SiteConfig()
)

data class UpdateSiteRequest(
    val name: String? = null,
    @field:Pattern(
        regexp = "^[a-z0-9][a-z0-9.-]+[a-z0-9](:\\d+)?$",
        message = "must be a valid domain (e.g. blog.site.com)"
    ) val domain: String? = null,
    val description: String? = null,
    val stylesUrl: String? = null,
    val availableThemes: List<Theme>? = null,
    val languages: List<Languages>? = null,
    val config: SiteConfig? = null,
    val requestVerification: Boolean = false,
    @field:Pattern(
        regexp = "^[a-zA-Z0-9-]{0,20}$",
        message = "prefix must be alphanumeric with dashes, max 20 chars"
    ) val prefix: String? = null
)
