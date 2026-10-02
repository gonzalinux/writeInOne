package com.gonzalinux.blogs

import com.gonzalinux.domain.Languages
import com.gonzalinux.domain.post.SitemapEntry
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SITEMAP_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

fun buildSitemap(
    domain: String,
    prefix: String,
    languages: List<Languages>,
    entries: List<SitemapEntry>
): String {
    val base = prefix.removePrefix("/").removeSuffix("/")
    val baseUrl = if (base.isEmpty()) "https://$domain" else "https://$domain/$base"
    val defaultLang = languages.firstOrNull()?.value

    fun alternates(urls: Map<String, String>): String {
        if (urls.size < 2) return ""
        val links = urls.map { (lang, url) ->
            "<xhtml:link rel=\"alternate\" hreflang=\"$lang\" href=\"$url\"/>"
        } + listOfNotNull(
            urls[defaultLang]?.let { "<xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"$it\"/>" }
        )
        return links.joinToString("")
    }

    val langUrls = languages.joinToString("\n") { lang ->
        val alts = alternates(languages.associate { it.value to "$baseUrl/${it.value}" })
        "    <url><loc>$baseUrl/${lang.value}</loc>$alts</url>"
    }

    val translationsByPost = entries.groupBy { it.postId }
        .mapValues { (_, group) -> group.associate { it.lang to "$baseUrl/${it.lang}/articles/${it.slug}" } }

    val postUrls = entries.joinToString("\n") { entry ->
        val lastMod = entry.lastMod.atZoneSameInstant(ZoneOffset.UTC).format(SITEMAP_DATE_FMT)
        val alts = alternates(translationsByPost.getValue(entry.postId))
        "    <url><loc>$baseUrl/${entry.lang}/articles/${entry.slug}</loc><lastmod>$lastMod</lastmod>$alts</url>"
    }

    return """<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
    <url><loc>$baseUrl/</loc></url>
$langUrls
$postUrls
</urlset>"""
}
