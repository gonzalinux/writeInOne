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

    val langUrls = languages.joinToString("\n") { lang ->
        "    <url><loc>$baseUrl/${lang.value}</loc></url>"
    }

    val postUrls = entries.joinToString("\n") { entry ->
        val lastMod = entry.lastMod.atZoneSameInstant(ZoneOffset.UTC).format(SITEMAP_DATE_FMT)
        "    <url><loc>$baseUrl/${entry.lang}/articles/${entry.slug}</loc><lastmod>$lastMod</lastmod></url>"
    }

    return """<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
    <url><loc>$baseUrl/</loc></url>
$langUrls
$postUrls
</urlset>"""
}
