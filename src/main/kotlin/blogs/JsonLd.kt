package com.gonzalinux.blogs

import com.gonzalinux.domain.post.Post
import com.gonzalinux.domain.post.PostTranslation
import com.gonzalinux.domain.site.Site
import com.gonzalinux.domain.tag.Tag
import tools.jackson.databind.json.JsonMapper

private val mapper = JsonMapper.builder().build()

/** Serialised for embedding in a `<script>` tag: `<` is escaped so a title can't close the tag. */
private fun toScriptJson(value: Any): String = mapper.writeValueAsString(value).replace("<", "\\u003c")

private const val CONTEXT = "https://schema.org"

fun siteJsonLd(site: Site, baseUrl: String, lang: String, title: String, description: String?): String {
    val url = "$baseUrl/$lang"
    val graph = listOf(
        buildMap {
            put("@type", "WebSite")
            put("name", site.name)
            put("url", url)
            put("inLanguage", lang)
            description?.let { put("description", it) }
        },
        buildMap {
            put("@type", "Blog")
            put("name", title)
            put("url", url)
            put("inLanguage", lang)
            description?.let { put("description", it) }
        }
    )
    return toScriptJson(mapOf("@context" to CONTEXT, "@graph" to graph))
}

fun postJsonLd(
    site: Site,
    baseUrl: String,
    lang: String,
    post: Post,
    translation: PostTranslation,
    tags: List<Tag>
): String {
    val postUrl = "$baseUrl/$lang/articles/${translation.slug}"
    val publisher = mapOf("@type" to "Organization", "name" to site.name)
    val article = buildMap {
        put("@type", "BlogPosting")
        put("headline", translation.title)
        put("url", postUrl)
        put("mainEntityOfPage", postUrl)
        put("inLanguage", lang)
        put("dateModified", translation.updatedAt.toString())
        put("author", publisher)
        put("publisher", publisher)
        post.publishedAt?.let { put("datePublished", it.toString()) }
        translation.excerpt?.let { put("description", it) }
        post.coverUrl?.let { put("image", it) }
        if (tags.isNotEmpty()) put("keywords", tags.joinToString(", ") { it.name })
    }
    val breadcrumbs = mapOf(
        "@type" to "BreadcrumbList",
        "itemListElement" to listOf(
            mapOf("@type" to "ListItem", "position" to 1, "name" to site.name, "item" to "$baseUrl/$lang"),
            mapOf("@type" to "ListItem", "position" to 2, "name" to translation.title, "item" to postUrl)
        )
    )
    return toScriptJson(mapOf("@context" to CONTEXT, "@graph" to listOf(article, breadcrumbs)))
}
