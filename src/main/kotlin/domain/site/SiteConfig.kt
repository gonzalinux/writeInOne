package com.gonzalinux.domain.site

data class SiteConfig(
    val faviconUrl: String? = null,
    val headHtml: String? = null,
    val bodyHtml: String? = null,
    val en: LangConfig = LangConfig(),
    val es: LangConfig = LangConfig()
)

data class LangConfig(
    val footer: String = "",
    val nav: List<NavLink> = emptyList(),
    val title: String? = null,
    val description: String? = null
)

data class NavLink(
    val label: String,
    val url: String
)
